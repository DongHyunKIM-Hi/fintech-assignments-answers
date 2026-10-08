package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 만든 포트폴리오. {@code todayBaseline*}는 "오늘 손익"의 기준값이다 (가정 G2):
 * 오늘 날짜에 처음 조회될 때의 평가금액을 그날의 기준으로 삼고, 생성 당일에는 생성 시점
 * 평가금액(= 매입금액, 손익 0)을 기준으로 시작한다.
 */
@Entity
@Getter
@Table(name = "portfolios")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private String name;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private final List<Holding> holdings = new ArrayList<>();

    private BigDecimal todayBaselineValue;
    private LocalDate todayBaselineDate;

    public Portfolio(String name, LocalDateTime createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public void replaceHoldings(List<Holding> newHoldings) {
        holdings.clear();
        for (Holding h : newHoldings) {
            h.assignTo(this);
            holdings.add(h);
        }
    }

    public void setTodayBaseline(BigDecimal value, LocalDate date) {
        this.todayBaselineValue = value;
        this.todayBaselineDate = date;
    }
}
