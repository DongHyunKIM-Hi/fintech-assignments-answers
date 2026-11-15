package com.practicefintech.portfolio.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포트폴리오가 보유한 종목 하나. 1단계는 코드와 수량만 들고 있다.
 *
 * <p>이 단계에서 일부러 하지 않는 것
 * <ul>
 *   <li><b>매입가 저장</b>: 담은 시점의 가격을 남기지 않는다. 그래서 매입금액을 현재가로 다시 계산하게 되고,
 *       매입금액과 평가금액이 늘 같아져 <b>수익률이 항상 0</b>이 된다. (README 6장, 시나리오 S7 위반)</li>
 *   <li><b>매입 시점 환율 저장</b>: 달러 상품도 원화 상품과 똑같이 다룬다. (가정 G3는 2단계 몫)</li>
 * </ul>
 * 2단계에서 {@code purchasePriceNative}와 {@code purchaseFxRate}를 추가해 매입가를 고정한다.
 */
@Entity
@Getter
@Table(name = "holdings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    private String code;
    private long quantity;

    public Holding(String code, long quantity) {
        this.code = code;
        this.quantity = quantity;
    }

    void assignTo(Portfolio portfolio) {
        this.portfolio = portfolio;
    }
}
