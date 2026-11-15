package com.practicefintech.portfolio.domain.portfolio.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequest {

    @NotBlank
    @Size(min = 1, max = 50)
    private String name;

    @NotEmpty
    @Size(min = 1, max = 20)
    private List<@Valid HoldingRequest> holdings;
}
