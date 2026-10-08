package com.practicefintech.refundrelay.domain.refund.model.response;

import lombok.Getter;

@Getter
public class RefundDuplicateResponse {

    private final String refundId;
    private final String status;
    private final boolean duplicate;
    private final String failureReason;

    public RefundDuplicateResponse(String refundId, String status, boolean duplicate, String failureReason) {
        this.refundId = refundId;
        this.status = status;
        this.duplicate = duplicate;
        this.failureReason = failureReason;
    }

    public RefundDuplicateResponse(String refundId, String status, String failureReason) {
        this(refundId, status, true, failureReason);
    }
}
