package com.practicefintech.coffee.domain.menu.service;

import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.menu.repository.PopularMenuRanking;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 최근 7일(오늘 포함) 동안 주문 횟수가 가장 많은 메뉴 3개를 조회한다.
 *
 * 평소에는 Redis 집계를 읽는다. 주문 테이블을 매번 GROUP BY 하지 않아도 되기 때문이다.
 * Redis에 장애가 나면 주문 테이블을 직접 집계한다. 주문 테이블이 원본 데이터이므로 결과는 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularMenuService {

    static final int DAYS = 7;
    private static final int LIMIT = 3;

    private final PopularMenuRanking ranking;
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final Clock clock;

    public List<PopularMenuResponse> getPopularMenus() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Long> counts = loadCounts(today);

        Map<Long, Menu> menus = menuRepository.findAllById(counts.keySet()).stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));

        // 주문 횟수가 같으면 메뉴 ID가 작은 쪽을 먼저 보여 준다. 같은 요청에 항상 같은 순서로 응답하기 위해서다.
        List<Map.Entry<Long, Long>> top = counts.entrySet().stream()
                .filter(entry -> menus.containsKey(entry.getKey()))
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(LIMIT)
                .toList();

        List<PopularMenuResponse> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Menu menu = menus.get(top.get(i).getKey());
            result.add(new PopularMenuResponse(i + 1, menu.getId(), menu.getName(), menu.getPrice(), top.get(i).getValue()));
        }
        return result;
    }

    private Map<Long, Long> loadCounts(LocalDate today) {
        try {
            List<LocalDate> dates = IntStream.range(0, DAYS).mapToObj(today::minusDays).toList();
            return ranking.sumCounts(dates);
        } catch (RuntimeException e) {
            log.warn("Redis 인기 메뉴 집계를 읽지 못해 주문 테이블에서 직접 집계합니다.", e);
            return orderRepository.countByMenuSince(today.minusDays(DAYS - 1).atStartOfDay()).stream()
                    .collect(Collectors.toMap(OrderRepository.MenuOrderCount::getMenuId,
                            OrderRepository.MenuOrderCount::getOrderCount));
        }
    }
}
