package com.practicefintech.coffee.domain.order.service;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.common.entity.UserPoint;
import com.practicefintech.coffee.common.enums.ErrorCode;
import com.practicefintech.coffee.common.exception.ApiException;
import com.practicefintech.coffee.domain.dataplatform.client.DataPlatformClient;
import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import com.practicefintech.coffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 커피 주문과 포인트 결제.
 *
 * 2단계에서 더한 것
 * 1. 없는 메뉴는 404 MENU_NOT_FOUND로 거절한다.
 * 2. 잔액이 모자라면 409 INSUFFICIENT_POINT로 거절하고 아무것도 바꾸지 않는다.
 * 3. 포인트 차감과 주문 저장을 한 트랜잭션으로 묶었다.
 *    중간에 실패하면 둘 다 롤백되므로, 돈만 빠지고 주문이 없는 상태가 생기지 않는다.
 * 4. 데이터 수집 플랫폼으로 주문 정보를 전송한다.
 *
 * [2단계에서 아직 하지 않는 것 — 3단계 몫]
 * 1. 동시 요청 보호가 없다. 잔액 확인(canPay)과 차감 사이에 다른 요청이 끼어들 수 있다.
 *    트랜잭션은 중간 결과를 숨겨 줄 뿐, 같은 행을 동시에 읽는 것을 막지 않는다.
 *    3잔 값만 충전하고 동시에 10건을 주문하면 10건이 모두 성공한다.
 *    → 3단계에서 사용자 단위 분산 락(또는 조건부 UPDATE, 비관적 락)으로 막는다.
 * 2. 데이터 수집 플랫폼 전송을 트랜잭션 안에서 동기로 호출한다. 두 가지 문제가 있다.
 *    - 플랫폼이 느리면 DB 트랜잭션을 그만큼 오래 잡는다.
 *    - 플랫폼 전송이 실패하면 멀쩡한 결제까지 롤백된다.
 *      반대로 전송한 뒤 커밋이 실패하면, 없는 주문을 보낸 셈이 된다.
 *    → 3단계에서 아웃박스 테이블(order_event)에 적어 두고 커밋된 뒤에 보내며,
 *      실패한 것은 스케줄러가 다시 보낸다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MenuRepository menuRepository;
    private final UserPointRepository userPointRepository;
    private final OrderRepository orderRepository;
    private final DataPlatformClient dataPlatformClient;
    private final Clock clock;

    @Transactional
    public OrderResponse order(Long userId, Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MENU_NOT_FOUND,
                        "존재하지 않는 메뉴입니다. menuId=" + menuId));

        UserPoint point = userPointRepository.findById(userId)
                .filter(p -> p.canPay(menu.getPrice()))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_POINT,
                        "포인트가 부족합니다. 주문 금액=" + menu.getPrice()));

        LocalDateTime now = LocalDateTime.now(clock);
        point.use(menu.getPrice(), now);
        CoffeeOrder order = orderRepository.save(CoffeeOrder.of(userId, menu, now));

        // 트랜잭션 안에서 외부 시스템을 직접 호출한다. 3단계에서 커밋 뒤 전송으로 옮긴다.
        dataPlatformClient.send(OrderEventPayload.from(order));

        return new OrderResponse(order.getId(), menu.getId(), menu.getName(), order.getPrice(),
                point.getBalance(), now);
    }
}
