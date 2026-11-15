package com.practicefintech.coffee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 1단계에서는 @EnableAsync, @EnableScheduling을 켜지 않는다.
 * 비동기 전송과 재전송 스케줄러가 아직 없기 때문이다. (2, 3단계에서 추가)
 */
@SpringBootApplication
public class CoffeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeApplication.class, args);
    }
}
