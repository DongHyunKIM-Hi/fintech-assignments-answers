package com.practicefintech.coffee.domain.order.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private Long menuId;
    private String menuName;
    private long paidAmount;
    private long remainingPoint;
    private LocalDateTime orderedAt;
}
