package com.practicefintech.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 2단계 — 예외 처리와 시나리오.
 *
 * <p>이 단계에서 일부러 하지 않는 것
 * <ul>
 *   <li>{@code @EnableScheduling}을 켜지 않는다. 30초 주기 시세 갱신은 3단계에서 만든다.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PortfolioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
