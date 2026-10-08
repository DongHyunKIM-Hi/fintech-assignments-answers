package com.practicefintech.portfolio.domain.price.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 가장 최근에 성공적으로 받아 온 시세·환율 스냅샷을 들고 있는다.
 * 시세 서버가 장애 상태여도 이 클래스는 마지막 성공 값을 그대로 돌려준다 (가정 G4, S11).
 * 스냅샷은 통째로 교체하는 불변 객체라 읽는 쪽(포트폴리오 계산)이 항상 일관된 값을 본다.
 */
@Component
public class PriceSnapshotStore {

    private record Snapshot(Map<String, BigDecimal> prices, Instant asOf, BigDecimal fxRate, Instant fxAsOf) {
    }

    private volatile Snapshot snapshot;
    private volatile boolean stale = false;

    public void update(Map<String, BigDecimal> prices, Instant asOf, BigDecimal fxRate, Instant fxAsOf) {
        this.snapshot = new Snapshot(Map.copyOf(prices), asOf, fxRate, fxAsOf);
        this.stale = false;
    }

    /** 갱신에 실패했을 때 호출한다. 값은 그대로 두고 "오래된 값"이라는 표시만 남긴다. */
    public void markStale() {
        this.stale = true;
    }

    public boolean isReady() {
        return snapshot != null;
    }

    public boolean isStale() {
        return stale;
    }

    public BigDecimal getPrice(String code) {
        return snapshot == null ? null : snapshot.prices().get(code);
    }

    public Instant getAsOf() {
        return snapshot == null ? null : snapshot.asOf();
    }

    public BigDecimal getFxRate() {
        return snapshot == null ? null : snapshot.fxRate();
    }
}
