package com.practicefintech.portfolio.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioListItem(Long id, String name, BigDecimal totalValue, BigDecimal returnRate, LocalDateTime createdAt) {
}
