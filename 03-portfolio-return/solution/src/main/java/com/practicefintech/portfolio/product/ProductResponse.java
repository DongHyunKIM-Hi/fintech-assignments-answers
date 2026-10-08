package com.practicefintech.portfolio.product;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductResponse(
        String code, String name, String nameEn, ProductType type, CurrencyCode currency,
        Integer riskLevel, String issuer, LocalDate listedDate, BigDecimal currentPrice
) {
}
