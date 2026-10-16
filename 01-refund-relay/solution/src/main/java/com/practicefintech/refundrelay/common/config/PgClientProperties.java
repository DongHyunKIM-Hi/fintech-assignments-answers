package com.practicefintech.refundrelay.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * prefix를 pg로 정한 이유: 수강생 README에 명시한 환경변수 이름이 PG_BASE_URL이다.
 * Spring Boot의 완화된 바인딩 규칙에 따라 pg.base-url 프로퍼티가 PG_BASE_URL 환경변수와 대응한다.
 * (prefix가 pgmock이었다면 환경변수 이름이 PGMOCK_BASE_URL이 되어 README와 달라진다.)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pg")
public class PgClientProperties {

    private String baseUrl;
    private long callTimeoutMs;
}
