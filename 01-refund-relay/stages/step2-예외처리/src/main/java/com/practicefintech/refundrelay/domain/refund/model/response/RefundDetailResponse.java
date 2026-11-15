package com.practicefintech.refundrelay.domain.refund.model.response;

import com.practicefintech.refundrelay.common.entity.Refund;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@AllArgsConstructor
public class RefundDetailResponse {

    private String refundId;
    private String orderId;
    private long refundAmount;
    private String status;
    private String failureReason;
    private int attemptCount;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    public static RefundDetailResponse from(Refund refund) {
        return new RefundDetailResponse(
                refund.getRefundId(),
                refund.getOrderId(),
                refund.getRefundAmount(),
                refund.getStatus().name(),
                refund.getFailureReason() == null ? null : refund.getFailureReason().name(),
                refund.getAttemptCount(),
                toLocal(refund.getRequestedAt()),
                toLocal(refund.getCompletedAt())
        );
    }

    /** API 명세의 시각 예시(2026-09-21T10:00:00)와 같은 로컬 시각 형식으로 응답한다. */
    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
