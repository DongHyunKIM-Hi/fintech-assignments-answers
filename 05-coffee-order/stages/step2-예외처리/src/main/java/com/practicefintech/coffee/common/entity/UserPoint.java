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
 * 2단계에서 잔액 확인(canPay, use)을 넣었다. 잔액이 모자라면 차감하지 않는다.
 *
 * [2단계에서 아직 하지 않는 것]
 * 이 확인은 한 요청 안에서만 맞다. 같은 사용자의 요청이 동시에 들어오면
 * 둘 다 "충분하다"를 통과한 뒤 같은 잔액을 덮어쓴다. → 3단계에서 분산 락으로 막는다.
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
