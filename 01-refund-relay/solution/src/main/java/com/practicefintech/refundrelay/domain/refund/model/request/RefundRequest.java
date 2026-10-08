package com.practicefintech.refundrelay.domain.refund.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundRequest {

    @NotBlank
    @Size(max = 50)
    private String refundId;

    @NotBlank
    @Size(max = 50)
    private String orderId;

    @Min(1)
    @Max(100_000_000)
    private long paidAmount;

    @Min(1)
    @Max(100_000_000)
    private long refundAmount;

    @Size(max = 200)
    private String reason;
}
