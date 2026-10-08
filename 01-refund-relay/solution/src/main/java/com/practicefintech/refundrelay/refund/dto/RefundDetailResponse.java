package com.practicefintech.refundrelay.refund.dto;

import com.practicefintech.refundrelay.refund.Refund;

import java.time.Instant;

public record RefundDetailResponse(
        String refundId,
        String orderId,
        long refundAmount,
        String status,
        String failureReason,
        int attemptCount,
        Instant requestedAt,
        Instant completedAt
) {
    public static RefundDetailResponse from(Refund refund) {
        return new RefundDetailResponse(
                refund.getRefundId(),
                refund.getOrderId(),
                refund.getRefundAmount(),
                refund.getStatus().name(),
                refund.getFailureReason() == null ? null : refund.getFailureReason().name(),
                refund.getAttemptCount(),
                refund.getRequestedAt(),
                refund.getCompletedAt()
        );
    }
}
