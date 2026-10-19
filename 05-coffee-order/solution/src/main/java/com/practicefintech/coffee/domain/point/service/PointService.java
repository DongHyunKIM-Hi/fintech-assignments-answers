package com.practicefintech.coffee.domain.point.service;

import com.practicefintech.coffee.common.entity.UserPoint;
import com.practicefintech.coffee.common.lock.DistributedLockExecutor;
import com.practicefintech.coffee.domain.point.model.response.PointResponse;
import com.practicefintech.coffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 포인트 충전과 조회.
 *
 * 충전과 주문 결제는 같은 사용자 락(point:{userId})을 쓴다.
 * 같은 사용자의 잔액 변경은 서버가 몇 대든 한 번에 하나씩만 일어나고,
 * 다른 사용자의 요청은 서로 기다리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointRepository;
    private final DistributedLockExecutor lockExecutor;
    private final Clock clock;

    public PointResponse charge(Long userId, long amount) {
        return lockExecutor.executeInTransaction(lockKey(userId), () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            // 첫 충전이면 잔액 0인 행을 만든다. 락 안이라 두 서버가 동시에 같은 행을 만들지 않는다.
            UserPoint point = userPointRepository.findById(userId)
                    .orElseGet(() -> userPointRepository.save(UserPoint.empty(userId, now)));
            point.charge(amount, now);
            return PointResponse.from(point);
        });
    }

    @Transactional(readOnly = true)
    public PointResponse getPoint(Long userId) {
        return userPointRepository.findById(userId)
                .map(PointResponse::from)
                .orElseGet(() -> new PointResponse(userId, 0));
    }

    public static String lockKey(Long userId) {
        return "point:" + userId;
    }
}
