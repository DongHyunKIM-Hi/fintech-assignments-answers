package com.practicefintech.portfolio.domain.portfolio.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code priceStale}는 명세에 없는 추가 필드다. (가정 G4)
 * 시세 서버 장애로 마지막으로 받은 시세를 계속 쓰고 있으면 true가 되어, 값이 오래되었을 수 있음을 알린다.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortfolioSummary {
    private BigDecimal totalCost;
    private BigDecimal totalValue;
    private BigDecimal profit;
    private BigDecimal returnRate;
    private BigDecimal todayProfit;
    private Instant asOf;
    private Boolean priceStale;
}
