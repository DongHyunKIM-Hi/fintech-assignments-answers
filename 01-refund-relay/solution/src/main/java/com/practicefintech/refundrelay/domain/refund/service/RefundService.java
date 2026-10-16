package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.exception.ErrorResponse;
import com.practicefintech.refundrelay.domain.refund.model.request.RefundRequest;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundAcceptedResponse;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundDetailResponse;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundDuplicateResponse;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundListResponse;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접수 로직: 같은 주문(orderId) 단위 락 안에서 중복 확인, 한도 확인, 저장을
 * 한 번에 수행한다. (시나리오 S2, S4, S5, S6)
 * DB의 refundId 유일 제약은 락이 동작하지 않는 상황(예: 서버 여러 대)에 대비한
 * 마지막 방어 수단이다. 정상 경로에서는 락이 먼저 중복을 막는다.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository repository;
    private final ConcurrentHashMap<String, Object> orderLocks = new ConcurrentHashMap<>();

    public ResponseEntity<?> accept(RefundRequest request) {
        Object lock = orderLocks.computeIfAbsent(request.getOrderId(), id -> new Object());
        synchronized (lock) {
            return acceptLocked(request);
        }
    }

    /**
     * 이 메서드는 항상 {@link #accept}의 synchronized 블록 안에서만 호출된다.
     * 같은 orderId에 대해서는 한 번에 한 스레드만 들어오므로, 별도의 DB 트랜잭션으로
     * 묶지 않아도 조회, 판단, 저장이 하나의 단위로 실행된다.
     * (1차 방어 수단은 자바 락, 2차 방어 수단은 Refund.refundId의 DB 유일 제약)
     */
    private ResponseEntity<?> acceptLocked(RefundRequest request) {
        Optional<Refund> existing = repository.findByRefundId(request.getRefundId());
        if (existing.isPresent()) {
            Refund refund = existing.get();
            if (!refund.sameContentAs(request.getOrderId(), request.getPaidAmount(), request.getRefundAmount())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                        "REFUND_ID_CONFLICT", "이미 존재하는 요청 ID이며 내용이 다릅니다.", request.getRefundId()));
            }
            String failureReason = refund.getFailureReason() == null ? null : refund.getFailureReason().name();
            return ResponseEntity.ok(new RefundDuplicateResponse(
                    refund.getRefundId(), refund.getStatus().name(), failureReason));
        }

        long reserved = repository.sumReservedAmount(request.getOrderId());
        if (reserved + request.getRefundAmount() > request.getPaidAmount()) {
            Refund rejected = Refund.rejected(request.getRefundId(), request.getOrderId(),
                    request.getPaidAmount(), request.getRefundAmount(), request.getReason(), Instant.now());
            saveNewRefund(rejected);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(
                    "REFUND_LIMIT_EXCEEDED", "환불 가능 금액을 초과했습니다.", request.getRefundId()));
        }

        Refund accepted = Refund.accepted(request.getRefundId(), request.getOrderId(),
                request.getPaidAmount(), request.getRefundAmount(), request.getReason(), Instant.now());
        saveNewRefund(accepted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefundAcceptedResponse(request.getRefundId()));
    }

    /**
     * 정상 경로에서는 orderId 락이 이미 같은 refundId의 동시 삽입을 막아 주므로
     * 유일 제약 위반은 거의 일어나지 않는다. 위반이 발생하면 예외를 잡아
     * 이미 저장된 요청으로 처리한다.
     */
    private void saveNewRefund(Refund refund) {
        try {
            repository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException e) {
            // 이미 같은 refundId가 저장되어 있다는 뜻. 호출자 쪽에서 재조회하도록 그대로 둔다.
            throw e;
        }
    }

    public ResponseEntity<?> getOne(String refundId) {
        Optional<Refund> found = repository.findByRefundId(refundId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                    "REFUND_NOT_FOUND", "해당 환불 요청을 찾을 수 없습니다.", refundId));
        }
        return ResponseEntity.ok(RefundDetailResponse.from(found.get()));
    }

    public RefundListResponse list(String orderId, int page, int size) {
        Page<Refund> result = repository.findByOrderIdOrderByRequestedAtDesc(orderId, PageRequest.of(page, size));
        return RefundListResponse.from(result);
    }
}
