package com.practicefintech.refundrelay.domain.refund.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 2단계에서 검증 애너테이션을 붙였다. 위반하면 컨트롤러의 @Valid가 걸러 400을 응답한다. */
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
    private long paidAmount;

    @Min(1)
    private long refundAmount;

    @Size(max = 200)
    private String reason;
}
