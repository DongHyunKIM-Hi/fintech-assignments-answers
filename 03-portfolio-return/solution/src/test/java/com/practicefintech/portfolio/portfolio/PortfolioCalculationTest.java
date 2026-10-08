package com.practicefintech.portfolio.portfolio;

import com.practicefintech.portfolio.price.PriceSnapshotStore;
import com.practicefintech.portfolio.product.CurrencyCode;
import com.practicefintech.portfolio.product.Product;
import com.practicefintech.portfolio.product.ProductRepository;
import com.practicefintech.portfolio.product.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 기준표 D "상" 수준 예시: 매입 시점 고정, 반올림 규칙, 통화 혼재 계산을 직접 검증한다.
 * 시세 서버 없이 {@link PriceSnapshotStore}를 직접 채워서 재현 가능하게 만들었다.
 */
@SpringBootTest
@Transactional
class PortfolioCalculationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PriceSnapshotStore priceSnapshotStore;
    @Autowired
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        productRepository.save(new Product("T001", "테스트 KRW 상품", "Test KRW", ProductType.ETF,
                CurrencyCode.KRW, 3, "테스트운용", LocalDate.now()));
        productRepository.save(new Product("T002", "테스트 USD 상품", "Test USD", ProductType.BOND,
                CurrencyCode.USD, 2, "테스트운용", LocalDate.now()));

        priceSnapshotStore.update(
                Map.of("T001", new BigDecimal("10000"), "T002", new BigDecimal("100.00")),
                Instant.now(), new BigDecimal("1300.00"), Instant.now());
    }

    @Test
    void purchasePriceStaysFixedWhenMarketPriceChanges() {
        PortfolioDetailResponse created = portfolioService.create(
                new PortfolioRequest("테스트", java.util.List.of(new HoldingRequest("T001", 10L))));

        // 시세가 바뀌어도
        priceSnapshotStore.update(Map.of("T001", new BigDecimal("15000"), "T002", new BigDecimal("100.00")),
                Instant.now(), new BigDecimal("1300.00"), Instant.now());

        PortfolioDetailResponse after = portfolioService.getDetail(created.id());
        assertEquals(new BigDecimal("10000"), after.holdings().get(0).purchasePrice()); // 매입가는 그대로
        assertEquals(new BigDecimal("15000"), after.holdings().get(0).currentPrice()); // 현재가만 바뀜
        assertEquals(new BigDecimal("150000"), after.summary().totalValue()); // 10 * 15000
        assertEquals(new BigDecimal("100000"), after.summary().totalCost()); // 10 * 10000 (고정)
    }

    @Test
    void mixedCurrencyPortfolioConvertsUsdUsingFxRate() {
        PortfolioDetailResponse created = portfolioService.create(new PortfolioRequest("환율 테스트",
                java.util.List.of(new HoldingRequest("T001", 1L), new HoldingRequest("T002", 2L))));

        // T001: 1 * 10000 = 10000, T002: 2 * 100.00 * 1300.00 = 260000, 합계 270000
        assertEquals(new BigDecimal("270000"), created.summary().totalCost());
    }

    @Test
    void returnRateRoundsHalfUpToTwoDecimals() {
        // 원가 30000, 평가금액 30100 -> 수익률 0.333...% -> 0.33
        productRepository.save(new Product("T003", "반올림 테스트", "Rounding", ProductType.FUND,
                CurrencyCode.KRW, 1, "테스트운용", LocalDate.now()));
        priceSnapshotStore.update(Map.of("T001", new BigDecimal("10000"), "T002", new BigDecimal("100.00"),
                        "T003", new BigDecimal("10000")),
                Instant.now(), new BigDecimal("1300.00"), Instant.now());

        PortfolioDetailResponse created = portfolioService.create(new PortfolioRequest("절사 테스트",
                java.util.List.of(new HoldingRequest("T003", 3L))));

        priceSnapshotStore.update(Map.of("T001", new BigDecimal("10000"), "T002", new BigDecimal("100.00"),
                        "T003", new BigDecimal("10033.3333")),
                Instant.now(), new BigDecimal("1300.00"), Instant.now());

        PortfolioDetailResponse after = portfolioService.getDetail(created.id());
        // 평가금액 = 3 * 10033.3333 = 30099.9999 -> 절사 30099, 원가 30000, 수익금 99
        assertEquals(new BigDecimal("30099"), after.summary().totalValue());
        assertEquals(new BigDecimal("99"), after.summary().profit());
        // 수익률 = 99/30000*100 = 0.33
        assertEquals(new BigDecimal("0.33"), after.summary().returnRate());
    }
}
