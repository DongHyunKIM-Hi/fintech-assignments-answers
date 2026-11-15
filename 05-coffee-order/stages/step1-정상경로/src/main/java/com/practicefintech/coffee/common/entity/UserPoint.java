package com.practicefintech.coffee.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자의 포인트 잔액 (1원 = 1P). 처음 충전할 때 만들어진다.
 *
 * [1단계에서 일부러 하지 않는 것]
 * 잔액이 충분한지 확인하지 않는다. use()는 시키는 대로 빼기만 한다.
 * 그래서 잔액보다 비싼 주문도 그대로 통과하려 든다.
 * 잔액 확인은 2단계에서, 여러 요청이 동시에 들어올 때의 보호는 3단계에서 넣는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint {

    @Id
    private Long userId;

    private long balance;

    private LocalDateTime updatedAt;

    public static UserPoint empty(Long userId, LocalDateTime now) {
        UserPoint point = new UserPoint();
        point.userId = userId;
        point.balance = 0;
        point.updatedAt = now;
        return point;
    }

    public void charge(long amount, LocalDateTime now) {
        this.balance += amount;
        this.updatedAt = now;
    }

    public void use(long amount, LocalDateTime now) {
        // 2단계에서는 여기(그리고 OrderService)에서 잔액을 확인하고 거절한다.
        this.balance -= amount;
        this.updatedAt = now;
    }
}
