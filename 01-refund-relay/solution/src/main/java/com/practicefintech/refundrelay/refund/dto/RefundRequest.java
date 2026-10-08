package com.practicefintech.refundrelay.refund.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(
        @NotBlank @Size(max = 50) String refundId,
        @NotBlank @Size(max = 50) String orderId,
        @Min(1) @Max(100_000_000) long paidAmount,
        @Min(1) @Max(100_000_000) long refundAmount,
        @Size(max = 200) String reason
) {
}
