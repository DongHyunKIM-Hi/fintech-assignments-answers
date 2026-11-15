package com.practicefintech.portfolio.domain.portfolio.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRequest {

    @NotBlank
    private String code;

    @NotNull
    @Min(1)
    @Max(1_000_000)
    private Long quantity;
}
