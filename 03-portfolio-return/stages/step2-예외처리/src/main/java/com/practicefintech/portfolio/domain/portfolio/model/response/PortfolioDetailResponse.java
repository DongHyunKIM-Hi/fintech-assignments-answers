package com.practicefintech.portfolio.domain.portfolio.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PortfolioDetailResponse {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private List<HoldingView> holdings;
    private PortfolioSummary summary;
}
