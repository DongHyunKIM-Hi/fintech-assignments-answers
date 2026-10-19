package com.practicefintech.coffee.common.entity;

import com.practicefintech.coffee.common.enums.OrderEventStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 데이터 수집 플랫폼에 보낼 주문 이벤트 (아웃박스 패턴).
 *
 * 주문과 같은 트랜잭션에서 저장한다. 주문이 커밋되면 이벤트도 함께 남고,
 * 주문이 롤백되면 이벤트도 남지 않는다. 전송에 실패해도 PENDING으로 남아 다시 보낸다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private Long userId;

    private Long menuId;

    private long amount;

    @Enumerated(EnumType.STRING)
    private OrderEventStatus status;

    private int attemptCount;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public static OrderEvent pending(CoffeeOrder order) {
        OrderEvent event = new OrderEvent();
        event.orderId = order.getId();
        event.userId = order.getUserId();
        event.menuId = order.getMenuId();
        event.amount = order.getPrice();
        event.status = OrderEventStatus.PENDING;
        event.attemptCount = 0;
        event.createdAt = order.getOrderedAt();
        return event;
    }
}
