package com.practicefintech.coffee.domain.point.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChargePointRequest {

    /** 1회 충전 한도. 잘못 입력한 큰 금액이 그대로 충전되는 것을 막는다. */
    public static final long MAX_CHARGE_AMOUNT = 1_000_000;

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Min(1)
    @Max(MAX_CHARGE_AMOUNT)
    private Long amount;
}
