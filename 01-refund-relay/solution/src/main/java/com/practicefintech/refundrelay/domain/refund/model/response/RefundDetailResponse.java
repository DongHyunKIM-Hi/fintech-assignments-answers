package com.practicefintech.refundrelay.domain.refund.model.response;

import com.practicefintech.refundrelay.common.entity.Refund;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class RefundDetailResponse {

    private String refundId;
    private String orderId;
    private long refundAmount;
    private String status;
    private String failureReason;
    private int attemptCount;
    private Instant requestedAt;
    private Instant completedAt;

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
