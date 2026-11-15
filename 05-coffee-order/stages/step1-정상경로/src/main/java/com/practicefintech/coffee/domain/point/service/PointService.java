package com.practicefintech.coffee.domain.point.service;

import com.practicefintech.coffee.common.entity.UserPoint;
import com.practicefintech.coffee.domain.point.model.response.PointResponse;
import com.practicefintech.coffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 포인트 충전과 조회.
 *
 * [1단계에서 일부러 하지 않는 것]
 * - 트랜잭션을 걸지 않는다. 읽기와 저장이 각각 따로 커밋된다.
 * - 동시성 제어가 없다. 같은 사용자의 충전이 동시에 들어오면 둘 다 같은 잔액을 읽고
 *   같은 값을 덮어쓰므로, 충전 몇 건이 사라진다(갱신 분실).
 * - 2단계에서 트랜잭션으로 묶고, 3단계에서 사용자 단위 분산 락으로 보호한다.
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointRepository;
    private final Clock clock;

    public PointResponse charge(Long userId, long amount) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserPoint point = userPointRepository.findById(userId)
                .orElseGet(() -> UserPoint.empty(userId, now));
        point.charge(amount, now);
        // 트랜잭션이 없으므로 변경 감지가 동작하지 않는다. 직접 저장해야 반영된다.
        return PointResponse.from(userPointRepository.save(point));
    }

    public PointResponse getPoint(Long userId) {
        return userPointRepository.findById(userId)
                .map(PointResponse::from)
                .orElseGet(() -> new PointResponse(userId, 0));
    }
}
