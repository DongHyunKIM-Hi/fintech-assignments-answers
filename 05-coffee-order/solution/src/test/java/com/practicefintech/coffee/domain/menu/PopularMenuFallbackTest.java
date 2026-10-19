package com.practicefintech.coffee.domain.menu;

import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.repository.PopularMenuRanking;
import com.practicefintech.coffee.domain.menu.service.PopularMenuService;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/** [데이터 일관성] Redis에 문제가 생겨도 주문은 성공하고, 인기 메뉴는 주문 테이블로 정확히 집계된다. */
class PopularMenuFallbackTest extends IntegrationTestSupport {

    @Autowired
    PopularMenuService popularMenuService;

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @MockitoSpyBean
    PopularMenuRanking ranking;

    @Test
    @DisplayName("[일관성] Redis 집계가 실패해도 결제는 유지되고, 조회는 주문 테이블 집계로 대신한다")
    void fallbackToDatabase() {
        doThrow(new IllegalStateException("Redis 장애")).when(ranking).increase(any(), any());
        doThrow(new IllegalStateException("Redis 장애")).when(ranking).sumCounts(anyList());
        pointService.charge(1L, 100_000);

        orderService.order(1L, 4L);
        orderService.order(1L, 4L);
        orderService.order(1L, 1L);

        assertThat(countRows("coffee_order")).isEqualTo(3);
        assertThat(popularMenuService.getPopularMenus())
                .extracting(PopularMenuResponse::getMenuId, PopularMenuResponse::getOrderCount)
                .containsExactly(tuple(4L, 2L), tuple(1L, 1L));
    }
}
