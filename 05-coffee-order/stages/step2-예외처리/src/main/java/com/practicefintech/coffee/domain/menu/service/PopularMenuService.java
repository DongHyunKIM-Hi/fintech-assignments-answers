package com.practicefintech.coffee.domain.menu.service;

import com.practicefintech.coffee.common.entity.Menu;
import com.practicefintech.coffee.domain.menu.model.response.PopularMenuResponse;
import com.practicefintech.coffee.domain.menu.repository.MenuRepository;
import com.practicefintech.coffee.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최근 7일(오늘 포함) 동안 주문 횟수가 가장 많은 메뉴 3개를 조회한다.
 *
 * 2단계에서 7일 범위를 넣었다. 주문 테이블을 원본으로 바로 집계하므로 횟수는 항상 정확하다.
 *
 * [2단계에서 아직 하지 않는 것]
 * 조회할 때마다 7일 치 주문을 전부 읽는다. 주문이 쌓일수록 느려진다.
 * → 3단계에서 Redis 정렬 집합에 날짜별로 횟수를 쌓아 두고 그것을 읽는다.
 *   대신 Redis가 멈추면 답을 줄 수 없게 되므로, 그때 이 쿼리로 되돌아오는 길(폴백)을 함께 만든다.
 */
@Service
@RequiredArgsConstructor
public class PopularMenuService {

    static final int DAYS = 7;
    private static final int LIMIT = 3;

    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final Clock clock;

    public List<PopularMenuResponse> getPopularMenus() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Long> counts = orderRepository
                .countByMenuSince(today.minusDays(DAYS - 1).atStartOfDay()).stream()
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
