package com.practicefintech.coffee.domain.order.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 주문 트랜잭션 안에서 발행하고, 커밋된 뒤에 리스너가 받는 이벤트 */
@Getter
@AllArgsConstructor
public class OrderCompletedEvent {

    private Long orderId;
    private Long orderEventId;
    private Long menuId;
    private LocalDateTime orderedAt;
}
