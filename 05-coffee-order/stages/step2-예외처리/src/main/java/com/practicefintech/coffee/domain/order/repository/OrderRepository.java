package com.practicefintech.coffee.domain.order.repository;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<CoffeeOrder, Long> {

    /** 2단계에서 기간 조건을 넣었다. 최근 7일(오늘 포함) 주문만 센다. */
    @Query("select o.menuId as menuId, count(o) as orderCount from CoffeeOrder o "
            + "where o.orderedAt >= :since group by o.menuId")
    List<MenuOrderCount> countByMenuSince(@Param("since") LocalDateTime since);

    interface MenuOrderCount {
        Long getMenuId();

        Long getOrderCount();
    }
}
