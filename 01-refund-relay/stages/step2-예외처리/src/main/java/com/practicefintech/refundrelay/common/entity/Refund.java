package com.practicefintech.refundrelay.common.entity;

import com.practicefintech.refundrelay.common.enums.FailureReason;
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
 * 2단계에서 실패 사유(failureReason)와 다음 시도 시각(nextAttemptAt)을 추가했다.
 * 이 두 칸이 있어야 "실패했지만 아직 끝난 게 아니라 0.5초 뒤에 다시 보낼 건"을 표현할 수 있다.
 *
 * 2단계에서 여전히 하지 않는 것
 * - refundId에 유일 제약(unique)을 걸지 않는다.
 *   같은 환불 ID가 동시에 들어오면 두 요청 모두 "기존 건 없음"으로 판단해 2건이 저장된다. (S4 위반)
 *
 * 왜 다음 단계가 필요한가
 * - 3단계에서 unique = true를 걸어 DB가 마지막으로 막게 하고,
 *   접수 로직에서는 주문 단위 락으로 조회와 저장을 한 덩어리로 묶는다.
 */
@Entity
@Getter
@Table(name = "refund", indexes = @Index(name = "idx_refund_order_id", columnList = "orderId"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 3단계에서 unique = true가 붙는 자리다.
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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private FailureReason failureReason;

    @Column(nullable = false)
    private int attemptCount;

    /** 워커가 이 시각 이후에만 이 건을 집어 처리한다. ACCEPTED 상태에서만 의미가 있다. */
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant completedAt;

    private Refund(String refundId, String orderId, long paidAmount, long refundAmount, String reason,
                    RefundStatus status, FailureReason failureReason, Instant requestedAt, Instant nextAttemptAt) {
        this.refundId = refundId;
        this.orderId = orderId;
        this.paidAmount = paidAmount;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.status = status;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount = 0;
    }

    /** 한도 안이라 접수된 건. 워커가 즉시(nextAttemptAt = now) 처리를 시도할 수 있다. */
    public static Refund accepted(String refundId, String orderId, long paidAmount, long refundAmount, String reason,
                                   Instant now) {
        return new Refund(refundId, orderId, paidAmount, refundAmount, reason, RefundStatus.ACCEPTED, null, now, now);
    }

    /** 한도 초과로 접수 단계에서 거절된 건. 재시도 대상이 아니므로 nextAttemptAt은 없다. */
    public static Refund rejected(String refundId, String orderId, long paidAmount, long refundAmount, String reason,
                                   Instant now) {
        Refund refund = new Refund(refundId, orderId, paidAmount, refundAmount, reason, RefundStatus.REJECTED,
                FailureReason.REFUND_LIMIT_EXCEEDED, now, null);
        refund.completedAt = now;
        return refund;
    }

    /** 같은 내용의 재요청인지 판단하는 기준(계약: orderId, paidAmount, refundAmount 일치, reason은 비교하지 않음). */
    public boolean sameContentAs(String orderId, long paidAmount, long refundAmount) {
        return this.orderId.equals(orderId) && this.paidAmount == paidAmount && this.refundAmount == refundAmount;
    }

    public void markSending() {
        // 처리 시도 자체는 상태를 바꾸지 않는다(외부에는 계속 ACCEPTED로 보임). 시도 횟수만 올린다.
        this.attemptCount += 1;
    }

    public void markCompleted(Instant completedAt) {
        this.status = RefundStatus.COMPLETED;
        this.failureReason = null;
        this.completedAt = completedAt;
        this.nextAttemptAt = null;
    }

    /** 재시도 여지가 남아 다음 시도 시각만 미루는 경우. 외부 상태는 여전히 ACCEPTED. */
    public void scheduleRetry(FailureReason reason, Instant nextAttemptAt) {
        this.failureReason = reason;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFinalFailure(FailureReason reason, Instant completedAt) {
        this.status = RefundStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = completedAt;
        this.nextAttemptAt = null;
    }
}
