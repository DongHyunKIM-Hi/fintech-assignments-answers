package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 만든 포트폴리오.
 *
 * <p>이 단계에서 일부러 하지 않는 것: 오늘 손익의 기준값({@code todayBaseline*})을 두지 않는다.
 * {@code todayProfit}은 2단계에서 계산한다.
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
}
