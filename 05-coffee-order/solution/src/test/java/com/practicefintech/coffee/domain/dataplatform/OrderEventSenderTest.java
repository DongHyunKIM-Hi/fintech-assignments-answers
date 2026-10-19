package com.practicefintech.coffee.domain.dataplatform;

import com.practicefintech.coffee.domain.dataplatform.client.MockDataPlatformClient;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.Await;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventSenderTest extends IntegrationTestSupport {

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @Autowired
    MockDataPlatformClient dataPlatform;

    @Test
    @DisplayName("주문이 커밋되면 바로 사용자 ID, 메뉴 ID, 결제 금액이 데이터 수집 플랫폼으로 전송된다")
    void sendRightAfterOrder() throws Exception {
        pointService.charge(7L, 10_000);

        OrderResponse order = orderService.order(7L, 3L);

        Await.until(() -> "SENT".equals(statusOf(order.getOrderId())), 3000);
        assertThat(dataPlatform.getReceived())
                .anySatisfy(payload -> {
                    assertThat(payload.getUserId()).isEqualTo(7L);
                    assertThat(payload.getMenuId()).isEqualTo(3L);
                    assertThat(payload.getAmount()).isEqualTo(5500);
                });
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM order_event WHERE order_id = ?", String.class, orderId);
    }
}
