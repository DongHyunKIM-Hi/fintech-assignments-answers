package com.practicefintech.refundrelay.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * prefix가 "pg"인 이유: 가이드가 수강생에게 약속한 환경변수 이름이 PG_BASE_URL이다.
 * Spring Boot의 완화된 바인딩 규칙상 "pg.base-url" 프로퍼티만 PG_BASE_URL 환경변수와 정확히
 * 대응한다("pgmock.base-url"이었다면 PGMOCK_BASE_URL이 되어 계약과 어긋난다).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pg")
public class PgClientProperties {

    private String baseUrl;
    private long callTimeoutMs;
}
