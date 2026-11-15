package com.practicefintech.refundrelay.domain.refund.model.response;

import lombok.Getter;

@Getter
public class RefundAcceptedResponse {

    private final String refundId;
    private final String status;
    private final boolean duplicate;

    public RefundAcceptedResponse(String refundId, String status, boolean duplicate) {
        this.refundId = refundId;
        this.status = status;
        this.duplicate = duplicate;
    }

    public RefundAcceptedResponse(String refundId) {
        this(refundId, "ACCEPTED", false);
    }
}
