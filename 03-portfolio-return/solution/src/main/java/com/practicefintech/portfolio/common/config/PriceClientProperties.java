package com.practicefintech.portfolio.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가상 시세 서버 연동 설정. prefix가 price이므로 환경변수 {@code PRICE_BASE_URL}이
 * {@link #getBaseUrl()}과 대응한다. (환경변수 이름은 지원자 README 3장에 명시)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "price")
public class PriceClientProperties {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private long callTimeoutMs;
    private long refreshIntervalMs;
    private long tokenRefreshMarginSeconds;
}
