package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
public class ExternalPricesResponse {
    private Instant asOf;
    private List<ExternalPriceQuote> prices;
}
