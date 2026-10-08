package com.practicefintech.portfolio.portfolio;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 포트폴리오가 보유한 종목 하나. {@code purchasePriceNative}는 담은 시점의 가격(상품 통화 기준)이고,
 * 그 이후 시세가 바뀌어도 바뀌지 않는다 (계약 6장, T4). 달러 상품은 담은 시점의 환율도 함께 저장해
 * 원가를 원화로 고정한다 (가정 G3).
 */
@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    private String code;
    private long quantity;
    private BigDecimal purchasePriceNative;

    /** 원화 상품은 null. 달러 상품은 담은 시점의 USD-KRW 환율. */
    private BigDecimal purchaseFxRate;

    protected Holding() {
    }

    public Holding(String code, long quantity, BigDecimal purchasePriceNative, BigDecimal purchaseFxRate) {
        this.code = code;
        this.quantity = quantity;
        this.purchasePriceNative = purchasePriceNative;
        this.purchaseFxRate = purchaseFxRate;
    }

    void assignTo(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getPurchasePriceNative() {
        return purchasePriceNative;
    }

    public BigDecimal getPurchaseFxRate() {
        return purchaseFxRate;
    }

    /** 매입 원가를 원화로 환산한다 (달러 상품은 담은 시점 환율로 고정, 가정 G3). */
    public BigDecimal costInKrw() {
        BigDecimal unitCostKrw = purchaseFxRate == null ? purchasePriceNative : purchasePriceNative.multiply(purchaseFxRate);
        return unitCostKrw.multiply(BigDecimal.valueOf(quantity));
    }
}
