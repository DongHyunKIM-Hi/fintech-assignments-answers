package com.practicefintech.portfolio.domain.product.model.response;

import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ProductResponse {
    private String code;
    private String name;
    private String nameEn;
    private ProductType type;
    private CurrencyCode currency;
    private Integer riskLevel;
    private String issuer;
    private LocalDate listedDate;
    private BigDecimal currentPrice;
}
