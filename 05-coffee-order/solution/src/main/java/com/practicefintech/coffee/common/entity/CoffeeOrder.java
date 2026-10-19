package com.practicefintech.coffee.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 주문 내역. 결제 금액은 주문 시점의 메뉴 가격을 그대로 남긴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoffeeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long menuId;

    private long price;

    private LocalDateTime orderedAt;

    public static CoffeeOrder of(Long userId, Menu menu, LocalDateTime now) {
        CoffeeOrder order = new CoffeeOrder();
        order.userId = userId;
        order.menuId = menu.getId();
        order.price = menu.getPrice();
        order.orderedAt = now;
        return order;
    }
}
