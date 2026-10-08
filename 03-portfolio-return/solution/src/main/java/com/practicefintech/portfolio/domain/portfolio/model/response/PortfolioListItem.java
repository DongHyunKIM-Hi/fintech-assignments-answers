package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PortfolioListItem {
    private Long id;
    private String name;
    private BigDecimal totalValue;
    private BigDecimal returnRate;
    private LocalDateTime createdAt;
}
