package com.practicefintech.portfolio.portfolio;

import java.math.BigDecimal;

public record HoldingView(String code, long quantity, BigDecimal purchasePrice, BigDecimal currentPrice) {
}
