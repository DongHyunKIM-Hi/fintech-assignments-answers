package com.practicefintech.coffee.domain.order;

import com.practicefintech.coffee.common.entity.OrderEvent;
import com.practicefintech.coffee.domain.order.repository.OrderEventRepository;
import com.practicefintech.coffee.domain.order.service.OrderService;
import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** [데이터 일관성] 주문 처리 중간에 실패하면 포인트 차감과 주문 저장이 모두 취소된다. */
class OrderRollbackTest extends IntegrationTestSupport {

    @Autowired
    OrderService orderService;

    @Autowired
    PointService pointService;

    @MockitoSpyBean
    OrderEventRepository orderEventRepository;

    @Test
    @DisplayName("[일관성] 포인트 차감, 주문 저장 다음 단계에서 실패하면 셋 다 롤백된다")
    void rollbackWhenEventSaveFails() {
        pointService.charge(1L, 10_000);
        doThrow(new IllegalStateException("이벤트 저장 실패")).when(orderEventRepository).save(any(OrderEvent.class));

        assertThatThrownBy(() -> orderService.order(1L, 1L)).isInstanceOf(IllegalStateException.class);

        assertThat(balanceOf(1L)).isEqualTo(10_000);
        assertThat(countRows("coffee_order")).isZero();
        assertThat(countRows("order_event")).isZero();
        assertThat(redisTemplate.keys("ranking:*")).isEmpty();
    }
}
