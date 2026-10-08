package com.practicefintech.portfolio.price.external;

import java.time.Instant;
import java.util.List;

public record ExternalPricesResponse(Instant asOf, List<ExternalPriceQuote> prices) {
}
