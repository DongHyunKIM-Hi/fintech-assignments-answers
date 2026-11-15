package com.practicefintech.coffee.domain.order.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long menuId;
}
