package com.practicefintech.coffee.domain.order.service;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.common.entity.OrderEvent;
import com.practicefintech.coffee.common.entity.UserPoint;
import com.practicefintech.coffee.common.enums.ErrorCode;
import com.practicefintech.coffee.common.exception.ApiException;
import com.practicefintech.coffee.common.lock.DistributedLockExecutor;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.order.model.dto.OrderCompletedEvent;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.repository.OrderEventRepository;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import com.practicefintech.coffee.domain.point.repository.UserPointRepository;
import com.practicefintech.coffee.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 커피 주문과 포인트 결제.
 *
 * 한 트랜잭션에서 세 가지를 함께 저장한다: 포인트 차감, 주문 내역, 데이터 수집 플랫폼에 보낼 이벤트.
 * 셋 중 하나라도 실패하면 모두 롤백되므로, 돈은 빠졌는데 주문이 없는 상태가 생기지 않는다.
 *
 * 인기 메뉴 집계(Redis)와 데이터 수집 플랫폼 전송(외부 호출)은 커밋된 뒤에 한다.
 * 외부 호출이 느리거나 실패해도 결제 트랜잭션과 락을 오래 잡지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MenuRepository menuRepository;
    private final UserPointRepository userPointRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final DistributedLockExecutor lockExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderResponse order(Long userId, Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MENU_NOT_FOUND,
                        "존재하지 않는 메뉴입니다. menuId=" + menuId));

        // 충전과 같은 사용자 락을 쓴다. 잔액 확인과 차감 사이에 다른 요청이 끼어들지 못한다.
        return lockExecutor.executeInTransaction(PointService.lockKey(userId), () -> {
            UserPoint point = userPointRepository.findById(userId)
                    .filter(p -> p.canPay(menu.getPrice()))
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_POINT,
                            "포인트가 부족합니다. 주문 금액=" + menu.getPrice()));

            LocalDateTime now = LocalDateTime.now(clock);
            point.use(menu.getPrice(), now);
            CoffeeOrder order = orderRepository.save(CoffeeOrder.of(userId, menu, now));
            OrderEvent event = orderEventRepository.save(OrderEvent.pending(order));

            eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), event.getId(), menu.getId(), now));

            return new OrderResponse(order.getId(), menu.getId(), menu.getName(), order.getPrice(),
                    point.getBalance(), now);
        });
    }
}
