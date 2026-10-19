package com.practicefintech.coffee.domain.menu.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 날짜별 메뉴 주문 횟수를 Redis 정렬 집합(ZSET)에 쌓는다.
 *
 * 키는 날짜마다 하나(ranking:menu:2026-10-19)이고, 값은 메뉴 ID, 점수는 주문 횟수다.
 * ZINCRBY는 Redis 안에서 원자적으로 실행되므로, 여러 서버가 동시에 올려도 횟수가 빠지지 않는다.
 * 최근 7일 조회는 7개 키를 ZUNION으로 합산한다. 키는 8일 뒤 자동으로 지워진다.
 */
@Repository
@RequiredArgsConstructor
public class PopularMenuRanking {

    private static final String KEY_PREFIX = "ranking:menu:";
    private static final Duration KEY_TTL = Duration.ofDays(8);

    private final StringRedisTemplate redisTemplate;

    public void increase(Long menuId, LocalDate orderDate) {
        String key = key(orderDate);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(menuId), 1);
        redisTemplate.expire(key, KEY_TTL);
    }

    /** 주어진 날짜들의 주문 횟수를 메뉴별로 합산한다. */
    public Map<Long, Long> sumCounts(List<LocalDate> dates) {
        List<String> keys = dates.stream().map(this::key).toList();
        Set<TypedTuple<String>> union = redisTemplate.opsForZSet()
                .unionWithScores(keys.get(0), keys.subList(1, keys.size()));

        Map<Long, Long> counts = new HashMap<>();
        if (union != null) {
            for (TypedTuple<String> tuple : union) {
                counts.put(Long.valueOf(tuple.getValue()), tuple.getScore().longValue());
            }
        }
        return counts;
    }

    private String key(LocalDate date) {
        return KEY_PREFIX + date;
    }
}
