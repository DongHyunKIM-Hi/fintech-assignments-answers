package com.practicefintech.coffee.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자의 포인트 잔액 (1원 = 1P).
 * 처음 충전할 때 만들어진다. 잔액 변경은 사용자 단위 분산 락 안에서만 일어난다.
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
        this.balance = Math.addExact(this.balance, amount);
        this.updatedAt = now;
    }

    public boolean canPay(long amount) {
        return balance >= amount;
    }

    public void use(long amount, LocalDateTime now) {
        if (!canPay(amount)) {
            throw new IllegalStateException("잔액보다 큰 금액은 차감할 수 없습니다.");
        }
        this.balance -= amount;
        this.updatedAt = now;
    }
}
