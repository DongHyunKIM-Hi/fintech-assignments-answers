package com.practicefintech.portfolio.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortfolioRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotEmpty @Size(min = 1, max = 20) List<@Valid HoldingRequest> holdings
) {
}
