package com.practicefintech.refundrelay.refund.dto;

public record RefundDuplicateResponse(String refundId, String status, boolean duplicate, String failureReason) {
    public RefundDuplicateResponse(String refundId, String status, String failureReason) {
        this(refundId, status, true, failureReason);
    }
}
