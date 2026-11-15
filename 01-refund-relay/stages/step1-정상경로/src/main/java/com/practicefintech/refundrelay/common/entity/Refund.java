package com.practicefintech.refundrelay.common.entity;

import com.practicefintech.refundrelay.common.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 환불 요청 1건.
 *
 * 1단계에서 일부러 하지 않는 것
 * - refundId에 유일 제약(unique)을 걸지 않는다. 같은 환불 ID가 두 번 저장될 수 있다.
 * - 실패 사유(failureReason)를 구분하지 않는다. 실패는 모두 FAILED로만 남는다.
 * - 재시도에 필요한 "다음 시도 시각"(nextAttemptAt)이 없다. 한 번 실패하면 끝이다.
 *
 * 왜 다음 단계가 필요한가
 * - 2단계: 실패 사유와 재시도 일정을 저장할 자리가 있어야 재시도 규칙(최대 4회)을 지킬 수 있다.
 * - 3단계: 같은 환불 ID가 동시에 들어와도 DB가 마지막으로 막아 주는 장치(유일 제약)가 필요하다.
 */
@Entity
@Getter
@Table(name = "refund", indexes = @Index(name = "idx_refund_order_id", columnList = "orderId"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 3단계에서 unique = true가 붙는 자리다. 지금은 같은 값이 여러 번 저장될 수 있다.
    @Column(nullable = false, length = 50)
    private String refundId;

    @Column(nullable = false, length = 50)
    private String orderId;

    @Column(nullable = false)
    private long paidAmount;

    @Column(nullable = false)
    private long refundAmount;

    @Column(length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant completedAt;

    private Refund(String refundId, String orderId, long paidAmount, long refundAmount, String reason,
                    RefundStatus status, Instant requestedAt) {
        this.refundId = refundId;
        this.orderId = orderId;
        this.paidAmount = paidAmount;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.status = status;
        this.requestedAt = requestedAt;
        this.attemptCount = 0;
    }

    /** 접수된 건. 워커가 바로 가져가 처리한다. */
    public static Refund accepted(String refundId, String orderId, long paidAmount, long refundAmount, String reason,
                                   Instant now) {
        return new Refund(refundId, orderId, paidAmount, refundAmount, reason, RefundStatus.ACCEPTED, now);
    }

    public void markSending() {
        // 처리 시도 자체는 상태를 바꾸지 않는다(외부에는 계속 ACCEPTED로 보임). 시도 횟수만 올린다.
        this.attemptCount += 1;
    }

    public void markCompleted(Instant completedAt) {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /**
     * 1단계는 한 번 실패하면 바로 최종 실패다.
     * 재시도(최대 4회 시도)와 실패 사유 기록은 2단계에서 추가한다.
     */
    public void markFailed(Instant completedAt) {
        this.status = RefundStatus.FAILED;
        this.completedAt = completedAt;
    }
}
