package com.practicefintech.coffee.domain.order;

import com.practicefintech.coffee.common.enums.ErrorCode;
import com.practicefintech.coffee.common.exception.ApiException;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.ConcurrentRunner;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest extends IntegrationTestSupport {

    private static final long AMERICANO = 1L;
    private static final long AMERICANO_PRICE = 4500;

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @Test
    @DisplayName("주문하면 포인트가 차감되고 주문 내역과 전송할 이벤트가 함께 저장된다")
    void order() {
        pointService.charge(1L, 10_000);

        OrderResponse response = orderService.order(1L, AMERICANO);

        assertThat(response.getPaidAmount()).isEqualTo(AMERICANO_PRICE);
        assertThat(response.getRemainingPoint()).isEqualTo(5500);
        assertThat(balanceOf(1L)).isEqualTo(5500);
        assertThat(countRows("coffee_order")).isEqualTo(1);
        assertThat(countRows("order_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("포인트가 부족하면 아무것도 바뀌지 않는다")
    void insufficientPoint() {
        pointService.charge(1L, 4000);

        assertThatThrownBy(() -> orderService.order(1L, AMERICANO))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT));
        assertThat(balanceOf(1L)).isEqualTo(4000);
        assertThat(countRows("coffee_order")).isZero();
        assertThat(countRows("order_event")).isZero();
    }

    @Test
    @DisplayName("없는 메뉴는 주문할 수 없다")
    void menuNotFound() {
        pointService.charge(1L, 10_000);

        assertThatThrownBy(() -> orderService.order(1L, 999L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MENU_NOT_FOUND));
        assertThat(balanceOf(1L)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("[동시성] 아메리카노 3잔 값만 있는 사용자가 동시에 10번 주문하면 3건만 성공하고 잔액은 0")
    void concurrentOrdersBySameUser() throws Exception {
        pointService.charge(1L, AMERICANO_PRICE * 3);

        List<ConcurrentRunner.Result<OrderResponse>> results =
                ConcurrentRunner.run(10, i -> orderService.order(1L, AMERICANO));

        assertThat(results.stream().filter(ConcurrentRunner.Result::success)).hasSize(3);
        assertThat(results.stream().filter(r -> !r.success()))
                .hasSize(7)
                .allMatch(r -> ((ApiException) r.error()).getCode() == ErrorCode.INSUFFICIENT_POINT);
        assertThat(balanceOf(1L)).isZero();
        assertThat(countRows("coffee_order")).isEqualTo(3);
    }

    @Test
    @DisplayName("[동시성] 같은 사용자의 충전과 주문이 동시에 섞여 들어와도 잔액이 맞는다")
    void concurrentChargeAndOrder() throws Exception {
        pointService.charge(1L, AMERICANO_PRICE * 10);

        // 짝수 번째는 4500P 충전, 홀수 번째는 아메리카노 주문. 각 10건
        List<ConcurrentRunner.Result<Object>> results = ConcurrentRunner.run(20, i -> i % 2 == 0
                ? pointService.charge(1L, AMERICANO_PRICE)
                : orderService.order(1L, AMERICANO));

        assertThat(results).allMatch(ConcurrentRunner.Result::success);
        assertThat(balanceOf(1L)).isEqualTo(AMERICANO_PRICE * 10);
        assertThat(countRows("coffee_order")).isEqualTo(10);
    }

    @Test
    @DisplayName("[동시성] 서로 다른 사용자 20명이 동시에 주문하면 모두 성공한다")
    void concurrentOrdersByDifferentUsers() throws Exception {
        for (long userId = 1; userId <= 20; userId++) {
            pointService.charge(userId, AMERICANO_PRICE);
        }

        List<ConcurrentRunner.Result<OrderResponse>> results =
                ConcurrentRunner.run(20, i -> orderService.order((long) i + 1, AMERICANO));

        assertThat(results).allMatch(ConcurrentRunner.Result::success);
        assertThat(countRows("coffee_order")).isEqualTo(20);
    }
}
