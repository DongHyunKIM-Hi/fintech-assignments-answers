package com.practicefintech.portfolio.domain.product.model.response;

import com.practicefintech.portfolio.common.enums.ProductType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 1단계에서는 {@code currency}와 {@code listedDate}가 CSV 원본 문자열이다.
 * 명세(5-1)는 통화를 KRW/USD로, 상장일을 yyyy-MM-dd로 응답하라고 하지만 그 정리는 2단계에서 한다.
 */
@Getter
@AllArgsConstructor
public class ProductResponse {
    private String code;
    private String name;
    private String nameEn;
    private ProductType type;
    private String currency;
    private String riskLevel;
    private String issuer;
    private String listedDate;
    private BigDecimal currentPrice;
}
