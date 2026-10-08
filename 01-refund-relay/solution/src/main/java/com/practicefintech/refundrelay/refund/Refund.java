package com.practicefintech.refundrelay.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 환불 요청 1건. refundId에 유니크 제약을 걸어 "같은 ID의 동시 요청"을 DB 차원에서
 * 막는 최종 방어선으로 삼는다 (주된 방어선은 RefundService의 주문 단위 락).
 */
@Entity
@Table(name = "refund", indexes = @Index(name = "idx_refund_order_id", columnList = "orderId"))
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
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

    protected Refund() {
        // JPA
    }

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

    public Long getId() {
        return id;
    }

    public String getRefundId() {
        return refundId;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getPaidAmount() {
        return paidAmount;
    }

    public long getRefundAmount() {
        return refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
