package com.practicefintech.refundrelay.refund.dto;

public record RefundAcceptedResponse(String refundId, String status, boolean duplicate) {
    public RefundAcceptedResponse(String refundId) {
        this(refundId, "ACCEPTED", false);
    }
}
