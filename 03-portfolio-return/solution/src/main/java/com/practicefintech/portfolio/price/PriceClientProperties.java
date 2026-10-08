package com.practicefintech.portfolio.price;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가상 시세 서버 연동 설정. prefix가 "price"이므로 환경변수 {@code PRICE_BASE_URL}이
 * {@link #baseUrl()}과 정확히 대응한다 (가이드 3장에서 약속한 이름).
 */
@ConfigurationProperties(prefix = "price")
public record PriceClientProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        long callTimeoutMs,
        long refreshIntervalMs,
        long tokenRefreshMarginSeconds
) {
}
