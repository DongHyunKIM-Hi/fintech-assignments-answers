package com.practicefintech.coffee.domain.order.repository;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<CoffeeOrder, Long> {

    /** Redis를 쓸 수 없을 때 인기 메뉴를 직접 집계하는 쿼리 */
    @Query("select o.menuId as menuId, count(o) as orderCount from CoffeeOrder o "
            + "where o.orderedAt >= :since group by o.menuId")
    List<MenuOrderCount> countByMenuSince(@Param("since") LocalDateTime since);

    long countByUserId(Long userId);

    interface MenuOrderCount {
        Long getMenuId();

        Long getOrderCount();
    }
}
