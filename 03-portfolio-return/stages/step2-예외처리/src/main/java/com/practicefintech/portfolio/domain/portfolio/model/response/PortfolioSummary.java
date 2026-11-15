package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 2단계에는 {@code priceStale} 필드가 없다. 시세를 보관하지 않고 요청마다 새로 받아 오므로
 * "지금 보여 주는 값이 오래된 값인지"를 알려 줄 방법 자체가 없다. (가정 G4는 3단계 몫)
 */
@Getter
@AllArgsConstructor
public class PortfolioSummary {
    private BigDecimal totalCost;
    private BigDecimal totalValue;
    private BigDecimal profit;
    private BigDecimal returnRate;
    private BigDecimal todayProfit;
    private Instant asOf;
}
