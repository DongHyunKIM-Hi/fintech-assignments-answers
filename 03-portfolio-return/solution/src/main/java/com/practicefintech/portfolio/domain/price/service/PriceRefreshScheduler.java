package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.domain.price.model.dto.ExternalFxResponse;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 30초마다(설정값) 가상 시세 서버에서 시세와 환율을 받아 {@link PriceSnapshotStore}를 갱신한다.
 * 시작 시 1회 즉시 실행하고, 실패하면 예외를 삼키고 마지막 스냅샷을 유지한다(S11, 가정 G4).
 */
@Component
public class PriceRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshScheduler.class);

    private final PriceClient priceClient;
    private final PriceSnapshotStore store;

    public PriceRefreshScheduler(PriceClient priceClient, PriceSnapshotStore store) {
        this.priceClient = priceClient;
        this.store = store;
    }

    @PostConstruct
    void refreshOnStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${price.refresh-interval-ms}", initialDelayString = "${price.refresh-interval-ms}")
    void refreshPeriodically() {
        refresh();
    }

    private void refresh() {
        try {
            ExternalPricesResponse prices = priceClient.fetchPrices();
            ExternalFxResponse fx = priceClient.fetchFx();
            Map<String, BigDecimal> byCode = new HashMap<>();
            for (ExternalPriceQuote quote : prices.getPrices()) {
                byCode.put(quote.getCode(), quote.getPrice());
            }
            store.update(byCode, prices.getAsOf(), fx.getRate(), fx.getAsOf());
        } catch (Exception e) {
            log.warn("시세 갱신 실패, 마지막 스냅샷을 유지합니다: {}", e.toString());
            store.markStale();
        }
    }
}
