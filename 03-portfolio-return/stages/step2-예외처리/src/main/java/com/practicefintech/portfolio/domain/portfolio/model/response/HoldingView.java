package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class HoldingView {
    private String code;
    private long quantity;
    private BigDecimal purchasePrice;
    private BigDecimal currentPrice;
}
