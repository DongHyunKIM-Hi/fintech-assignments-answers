package com.practicefintech.coffee.domain.menu.service;

import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 횟수가 가장 많은 메뉴 3개를 조회한다.
 *
 * [1단계에서 일부러 하지 않는 것]
 * - 최근 7일 범위가 없다. 오래된 주문까지 전부 센다. → 2단계
 * - 조회할 때마다 주문 테이블을 통째로 GROUP BY 한다. 주문이 쌓이면 느려진다.
 *   → 3단계에서 Redis 집계로 바꾸고, Redis 장애 때 이 쿼리로 되돌아오게 한다.
 */
@Service
@RequiredArgsConstructor
public class PopularMenuService {

    private static final int LIMIT = 3;

    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;

    public List<PopularMenuResponse> getPopularMenus() {
        Map<Long, Long> counts = orderRepository.countByMenu().stream()
                .collect(Collectors.toMap(OrderRepository.MenuOrderCount::getMenuId,
                        OrderRepository.MenuOrderCount::getOrderCount));

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
}
