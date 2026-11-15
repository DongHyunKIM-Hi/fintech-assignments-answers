package com.practicefintech.coffee.domain.point.service;

import com.practicefintech.coffee.common.entity.UserPoint;
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
 * 2단계에서 트랜잭션을 걸었다. 읽기와 저장이 한 덩어리로 커밋된다.
 *
 * [2단계에서 아직 하지 않는 것]
 * 트랜잭션은 "전부 되거나 전부 안 되거나"를 보장할 뿐, 동시에 들어온 요청을 줄 세우지는 않는다.
 * 같은 사용자의 충전 100건이 동시에 들어오면 대부분이 같은 잔액을 읽고 같은 값을 덮어써
 * 충전이 사라진다(갱신 분실). 처음 충전하는 사용자의 행을 여러 요청이 동시에 만들려다
 * 기본 키 충돌이 나기도 한다. → 3단계에서 사용자 단위 분산 락으로 막는다.
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointRepository;
    private final Clock clock;

    @Transactional
    public PointResponse charge(Long userId, long amount) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserPoint point = userPointRepository.findById(userId)
                .orElseGet(() -> userPointRepository.save(UserPoint.empty(userId, now)));
        point.charge(amount, now);
        return PointResponse.from(point);
    }

    @Transactional(readOnly = true)
    public PointResponse getPoint(Long userId) {
        return userPointRepository.findById(userId)
                .map(PointResponse::from)
                .orElseGet(() -> new PointResponse(userId, 0));
    }
}
