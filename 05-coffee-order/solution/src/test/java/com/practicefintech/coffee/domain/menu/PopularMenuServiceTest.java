package com.practicefintech.coffee.domain.menu;

import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.repository.PopularMenuRanking;
import com.practicefintech.coffee.domain.menu.service.PopularMenuService;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.ConcurrentRunner;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PopularMenuServiceTest extends IntegrationTestSupport {

    @Autowired
    PopularMenuService popularMenuService;

    @Autowired
    PopularMenuRanking ranking;

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @Autowired
    Clock clock;

    @Test
    @DisplayName("주문 횟수가 많은 순으로 3개를 고르고, 횟수가 같으면 메뉴 ID가 작은 쪽이 앞선다")
    void top3() {
        pointService.charge(1L, 100_000);
        orderTimes(3, 3);
        orderTimes(2, 2);
        orderTimes(1, 2);
        orderTimes(4, 1);

        List<PopularMenuResponse> result = popularMenuService.getPopularMenus();

        assertThat(result).extracting(PopularMenuResponse::getRank, PopularMenuResponse::getMenuId,
                        PopularMenuResponse::getOrderCount)
                .containsExactly(tuple(1, 3L, 3L), tuple(2, 1L, 2L), tuple(3, 2L, 2L));
    }

    @Test
    @DisplayName("오늘을 포함한 최근 7일만 센다. 7일 전 주문은 빠진다")
    void onlyLast7Days() {
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < 5; i++) {
            ranking.increase(5L, today.minusDays(6));
        }
        for (int i = 0; i < 10; i++) {
            ranking.increase(6L, today.minusDays(7));
        }
        ranking.increase(1L, today);

        List<PopularMenuResponse> result = popularMenuService.getPopularMenus();

        assertThat(result).extracting(PopularMenuResponse::getMenuId, PopularMenuResponse::getOrderCount)
                .containsExactly(tuple(5L, 5L), tuple(1L, 1L));
    }

    @Test
    @DisplayName("[동시성] 50명이 같은 메뉴를 동시에 주문해도 주문 횟수는 정확히 50")
    void concurrentOrdersAreCountedExactly() throws Exception {
        for (long userId = 1; userId <= 50; userId++) {
            pointService.charge(userId, 10_000);
        }

        ConcurrentRunner.run(50, i -> orderService.order((long) i + 1, 2L));

        List<PopularMenuResponse> result = popularMenuService.getPopularMenus();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderCount()).isEqualTo(50).isEqualTo(countRows("coffee_order"));
    }

    private void orderTimes(long menuId, int times) {
        for (int i = 0; i < times; i++) {
            orderService.order(1L, menuId);
        }
    }
}
