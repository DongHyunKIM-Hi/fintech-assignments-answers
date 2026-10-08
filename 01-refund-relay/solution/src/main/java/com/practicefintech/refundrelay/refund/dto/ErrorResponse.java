package com.practicefintech.refundrelay.refund.dto;

public record ErrorResponse(String code, String message, String refundId) {
}
