package com.practicefintech.portfolio.price.external;

import java.math.BigDecimal;

public record ExternalPriceQuote(String code, BigDecimal price, String currency) {
}
