package com.practicefintech.portfolio.portfolio;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code priceStale}는 계약에 없는 추가 필드다 (가정 G4): 시세 서버 장애로 마지막 성공 시세를
 * 계속 쓰고 있을 때 true가 되어, 값이 오래된 것일 수 있음을 클라이언트에 알린다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioSummary(
        BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit, BigDecimal returnRate,
        BigDecimal todayProfit, Instant asOf, Boolean priceStale
) {
}
