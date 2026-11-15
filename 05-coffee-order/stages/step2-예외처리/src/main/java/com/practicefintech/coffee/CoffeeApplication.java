package com.practicefintech.coffee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 2단계에서도 @EnableAsync, @EnableScheduling은 켜지 않는다.
 * 데이터 수집 플랫폼으로는 보내지만 아직 동기 호출이고, 재전송 스케줄러가 없기 때문이다. (3단계)
 */
@SpringBootApplication
public class CoffeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeApplication.class, args);
    }
}
