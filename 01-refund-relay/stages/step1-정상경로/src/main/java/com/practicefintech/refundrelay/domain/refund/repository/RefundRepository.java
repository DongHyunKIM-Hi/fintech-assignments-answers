package com.practicefintech.refundrelay.domain.refund.repository;

import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 1단계에서 일부러 하지 않는 것
 * - 한도 합계를 구하는 질의(sumReservedAmount)가 없다. 2단계에서 추가한다.
 * - 주문별 목록 조회(페이지 나누기)가 없다. 2단계에서 추가한다.
 */
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundId(String refundId);

    /** 워커가 처리할 건들. 접수가 빠른 순서대로 꺼낸다. */
    List<Refund> findByStatusOrderByRequestedAtAsc(RefundStatus status);
}
