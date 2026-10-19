package com.practicefintech.coffee.domain.dataplatform;

import com.practicefintech.coffee.domain.dataplatform.client.DataPlatformClient;
import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;
import com.practicefintech.coffee.domain.dataplatform.service.OrderEventRetryScheduler;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.Await;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** [데이터 일관성] 데이터 수집 플랫폼이 실패해도 주문은 유지되고, 이벤트는 나중에 다시 전송된다. */
@TestPropertySource(properties = "coffee.outbox-retry-grace-seconds=0")
class OrderEventRetryTest extends IntegrationTestSupport {

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @Autowired
    OrderEventRetryScheduler retryScheduler;

    @MockitoBean
    DataPlatformClient dataPlatformClient;

    @Test
    @DisplayName("[일관성] 첫 전송이 실패하면 PENDING으로 남고, 재전송 스케줄러가 보내면 SENT가 된다")
    void retryAfterFailure() throws Exception {
        doThrow(new IllegalStateException("플랫폼 장애"))
                .doNothing()
                .when(dataPlatformClient).send(any(OrderEventPayload.class));
        pointService.charge(1L, 10_000);

        OrderResponse order = orderService.order(1L, 1L);

        // 전송 실패와 관계없이 결제와 주문은 그대로 남는다
        assertThat(balanceOf(1L)).isEqualTo(5500);
        Await.until(() -> attemptCountOf(order.getOrderId()) == 1, 3000);
        assertThat(statusOf(order.getOrderId())).isEqualTo("PENDING");

        retryScheduler.retryPendingEvents();

        assertThat(statusOf(order.getOrderId())).isEqualTo("SENT");
        verify(dataPlatformClient, times(2)).send(any(OrderEventPayload.class));
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM order_event WHERE order_id = ?", String.class, orderId);
    }

    private int attemptCountOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM order_event WHERE order_id = ?", Integer.class, orderId);
    }
}
