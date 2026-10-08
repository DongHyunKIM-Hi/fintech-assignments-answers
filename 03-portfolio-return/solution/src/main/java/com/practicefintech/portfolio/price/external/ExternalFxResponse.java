package com.practicefintech.portfolio.price.external;

import java.math.BigDecimal;
import java.time.Instant;

public record ExternalFxResponse(String pair, BigDecimal rate, Instant asOf) {
}
