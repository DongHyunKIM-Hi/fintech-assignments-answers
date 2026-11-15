package com.practicefintech.coffee.domain.order.repository;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<CoffeeOrder, Long> {

    /**
     * 메뉴별 주문 횟수를 센다.
     *
     * [1단계에서 일부러 하지 않는 것]
     * 기간 조건이 없다. 요구사항은 "최근 7일"인데 지금은 처음부터 지금까지를 다 센다.
     * 7일 범위는 2단계에서 넣는다.
     */
    @Query("select o.menuId as menuId, count(o) as orderCount from CoffeeOrder o group by o.menuId")
    List<MenuOrderCount> countByMenu();

    interface MenuOrderCount {
        Long getMenuId();

        Long getOrderCount();
    }
}
