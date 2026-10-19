package com.practicefintech.coffee.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 주문 시각과 인기 메뉴의 날짜 계산에 쓰는 시계.
 * 테스트에서 날짜를 바꿔 볼 수 있도록 빈으로 둔다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
