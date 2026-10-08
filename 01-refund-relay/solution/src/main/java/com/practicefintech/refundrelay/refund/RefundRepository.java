package com.practicefintech.refundrelay.refund;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundId(String refundId);

    Page<Refund> findByOrderIdOrderByRequestedAtDesc(String orderId, Pageable pageable);

    @Query("select coalesce(sum(r.refundAmount), 0) from Refund r "
            + "where r.orderId = :orderId and r.status in (com.practicefintech.refundrelay.refund.RefundStatus.ACCEPTED, "
            + "com.practicefintech.refundrelay.refund.RefundStatus.COMPLETED)")
    long sumReservedAmount(@Param("orderId") String orderId);

    @Query("select distinct r.orderId from Refund r where r.status = com.practicefintech.refundrelay.refund.RefundStatus.ACCEPTED "
            + "and r.nextAttemptAt <= :now")
    List<String> findOrderIdsWithDueRefunds(@Param("now") Instant now);

    Optional<Refund> findFirstByOrderIdAndStatusAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(
            String orderId, RefundStatus status, Instant now);
}
