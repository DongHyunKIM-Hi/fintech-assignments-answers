package com.practicefintech.coffee.domain.order.service;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.common.entity.UserPoint;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import com.practicefintech.coffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 커피 주문과 포인트 결제.
 *
 * 1단계의 목표는 "설계한 흐름이 도는지"만 확인하는 것이다.
 * 메뉴를 찾고, 포인트를 빼고, 주문을 남긴다. 그게 전부다.
 *
 * [1단계에서 일부러 하지 않는 것]
 * 1. 잔액 확인이 없다. 잔액이 모자라도 그대로 빼려고 한다.
 *    (schema.sql의 CHECK (balance >= 0) 제약에 걸려 500으로 터진다.
 *     DB 제약이 없었다면 잔액이 그냥 음수가 된다. → 2단계에서 409로 거절)
 * 2. 없는 메뉴를 주문해도 404가 아니라 500이 나간다. → 2단계
 * 3. 트랜잭션이 없다. 포인트 차감과 주문 저장이 각각 따로 커밋되므로,
 *    주문 저장이 실패하면 포인트만 빠진 상태가 남는다. → 2단계
 * 4. 데이터 수집 플랫폼 전송이 없다. → 2단계
 * 5. 동시 요청 보호가 없다. 같은 사용자의 주문 10건이 동시에 들어오면
 *    모두 같은 잔액을 읽고 같은 값을 덮어쓴다. → 3단계
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MenuRepository menuRepository;
    private final UserPointRepository userPointRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderResponse order(Long userId, Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다. menuId=" + menuId));
        UserPoint point = userPointRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("포인트 정보가 없습니다. userId=" + userId));

        LocalDateTime now = LocalDateTime.now(clock);
        point.use(menu.getPrice(), now);
        userPointRepository.save(point);

        CoffeeOrder order = orderRepository.save(CoffeeOrder.of(userId, menu, now));

        return new OrderResponse(order.getId(), menu.getId(), menu.getName(), order.getPrice(),
                point.getBalance(), now);
    }
}
