package com.practicefintech.coffee.domain.order.repository;

import com.practicefintech.coffee.common.entity.OrderEvent;
import com.practicefintech.coffee.common.enums.OrderEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OrderEventStatus status, LocalDateTime before);

    long countByStatus(OrderEventStatus status);

    /** PENDING일 때만 SENT로 바꾼다. 이미 다른 곳에서 보내 SENT가 됐다면 0을 돌려준다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OrderEvent e set e.status = 'SENT', e.sentAt = :now, e.attemptCount = e.attemptCount + 1 "
            + "where e.id = :id and e.status = 'PENDING'")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OrderEvent e set e.attemptCount = e.attemptCount + 1 where e.id = :id")
    int increaseAttempt(@Param("id") Long id);
}
