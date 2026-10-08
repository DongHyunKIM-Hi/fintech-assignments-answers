package com.practicefintech.portfolio.portfolio;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioDetailResponse(
        Long id, String name, LocalDateTime createdAt, List<HoldingView> holdings, PortfolioSummary summary
) {
}
