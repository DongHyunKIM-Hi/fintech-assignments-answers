package com.practicefintech.coffee.domain.menu.service;

import com.practicefintech.coffee.domain.menu.repository.PopularMenuRanking;
import com.practicefintech.coffee.domain.order.model.dto.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문이 커밋된 뒤에만 인기 메뉴 집계를 올린다.
 * 커밋 전에 올리면, 주문이 롤백됐는데 집계에는 남는 일이 생긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularMenuEventListener {

    private final PopularMenuRanking ranking;

    @TransactionalEventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        try {
            ranking.increase(event.getMenuId(), event.getOrderedAt().toLocalDate());
        } catch (RuntimeException e) {
            // 집계 실패로 이미 끝난 결제를 되돌리지 않는다. 주문 테이블에는 남아 있으므로 다시 계산할 수 있다.
            log.error("인기 메뉴 집계 반영 실패. orderId={}", event.getOrderId(), e);
        }
    }
}
