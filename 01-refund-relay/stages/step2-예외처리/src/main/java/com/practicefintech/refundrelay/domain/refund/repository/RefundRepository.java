package com.practicefintech.refundrelay.domain.refund.repository;

import com.practicefintech.refundrelay.common.entity.Refund;
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

    /** 2단계에서 추가. 목록 조회용(최신순). */
    Page<Refund> findByOrderIdOrderByRequestedAtDesc(String orderId, Pageable pageable);

    /** 2단계에서 추가. 한도 계산용. REJECTED와 FAILED는 환불되지 않은 건이라 합계에서 뺀다. */
    @Query("select coalesce(sum(r.refundAmount), 0) from Refund r "
            + "where r.orderId = :orderId and r.status in (com.practicefintech.refundrelay.common.enums.RefundStatus.ACCEPTED, "
            + "com.practicefintech.refundrelay.common.enums.RefundStatus.COMPLETED)")
    long sumReservedAmount(@Param("orderId") String orderId);

    /**
     * 2단계에서 추가. 지금 보낼 차례가 된 건들(처음 시도 또는 재시도 대기가 끝난 건).
     *
     * 3단계에서는 이 질의가 "건 목록"이 아니라 "처리할 주문 ID 목록"으로 바뀐다.
     * 주문 단위로 묶어야 같은 주문의 앞 건이 끝나기 전에 다음 건을 보내지 않을 수 있기 때문이다.
     */
    @Query("select r from Refund r where r.status = com.practicefintech.refundrelay.common.enums.RefundStatus.ACCEPTED "
            + "and r.nextAttemptAt <= :now order by r.requestedAt asc")
    List<Refund> findDueRefunds(@Param("now") Instant now);
}
