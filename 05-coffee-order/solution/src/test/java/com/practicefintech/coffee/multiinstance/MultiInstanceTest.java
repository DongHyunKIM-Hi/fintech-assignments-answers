package com.practicefintech.coffee.multiinstance;

import com.practicefintech.coffee.CoffeeApplication;
import com.practicefintech.coffee.domain.menu.service.PopularMenuService;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.ConcurrentRunner;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [다수 인스턴스] 같은 MySQL과 Redis를 쓰는 서버를 하나 더 띄워, 두 서버에 요청을 나눠 보낸다.
 *
 * 테스트가 쓰는 서버(A)와 여기서 따로 띄운 서버(B)는 메모리를 공유하지 않는다.
 * synchronized 같은 JVM 락이었다면 A와 B의 요청이 서로를 막지 못해 이 테스트가 실패한다.
 */
class MultiInstanceTest extends IntegrationTestSupport {

    private static final long AMERICANO = 1L;
    private static final long AMERICANO_PRICE = 4500;

    static ConfigurableApplicationContext serverB;

    @Autowired
    OrderService orderServiceA;

    @Autowired
    PointService pointServiceA;

    @Autowired
    PopularMenuService popularMenuService;

    @BeforeAll
    static void startServerB() {
        // 명령줄 인자로 넘겨야 application.yml의 기본 접속 정보보다 우선한다
        serverB = new SpringApplicationBuilder(CoffeeApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--coffee.outbox-retry-millis=3600000");
    }

    @AfterAll
    static void stopServerB() {
        serverB.close();
    }

    @Test
    @DisplayName("[다수 인스턴스] 두 서버에 나눠 들어온 같은 사용자의 충전 100건이 모두 반영된다")
    void chargeAcrossInstances() throws Exception {
        PointService pointServiceB = serverB.getBean(PointService.class);

        List<ConcurrentRunner.Result<Object>> results = ConcurrentRunner.run(100, i -> i % 2 == 0
                ? pointServiceA.charge(1L, 100)
                : pointServiceB.charge(1L, 100));

        assertThat(results).allMatch(ConcurrentRunner.Result::success);
        assertThat(balanceOf(1L)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("[다수 인스턴스] 3잔 값만 있는 사용자의 주문 10건이 두 서버에 나눠 들어와도 3건만 성공한다")
    void orderAcrossInstances() throws Exception {
        OrderService orderServiceB = serverB.getBean(OrderService.class);
        pointServiceA.charge(1L, AMERICANO_PRICE * 3);

        List<ConcurrentRunner.Result<OrderResponse>> results = ConcurrentRunner.run(10, i -> i % 2 == 0
                ? orderServiceA.order(1L, AMERICANO)
                : orderServiceB.order(1L, AMERICANO));

        assertThat(results.stream().filter(ConcurrentRunner.Result::success)).hasSize(3);
        assertThat(balanceOf(1L)).isZero();
        assertThat(countRows("coffee_order")).isEqualTo(3);
        assertThat(popularMenuService.getPopularMenus().get(0).getOrderCount()).isEqualTo(3);
    }
}
