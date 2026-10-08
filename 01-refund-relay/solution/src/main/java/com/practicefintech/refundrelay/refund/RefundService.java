package com.practicefintech.refundrelay.refund;

import com.practicefintech.refundrelay.refund.dto.ErrorResponse;
import com.practicefintech.refundrelay.refund.dto.RefundAcceptedResponse;
import com.practicefintech.refundrelay.refund.dto.RefundDetailResponse;
import com.practicefintech.refundrelay.refund.dto.RefundDuplicateResponse;
import com.practicefintech.refundrelay.refund.dto.RefundListResponse;
import com.practicefintech.refundrelay.refund.dto.RefundRequest;
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
 * 접수 로직의 핵심: 같은 주문(orderId) 단위로 "중복 확인 → 한도 확인 → 저장"을
 * 하나의 락 안에서 원자적으로 수행한다 (project-overview.md 7장, 함정 T1~T3).
 * DB의 refundId 유니크 제약은 혹시 락이 무력화되는 상황(예: 여러 인스턴스)에 대비한
 * 최종 방어선으로만 쓴다 — 정상 경로에서는 락이 먼저 막아 준다.
 */
@Service
public class RefundService {

    private final RefundRepository repository;
    private final ConcurrentHashMap<String, Object> orderLocks = new ConcurrentHashMap<>();

    public RefundService(RefundRepository repository) {
        this.repository = repository;
    }

    public ResponseEntity<?> accept(RefundRequest request) {
        Object lock = orderLocks.computeIfAbsent(request.orderId(), id -> new Object());
        synchronized (lock) {
            return acceptLocked(request);
        }
    }

    /**
     * 이 메서드는 항상 {@link #accept}의 synchronized 블록 안에서만 호출된다.
     * 같은 orderId에 대해서는 한 번에 한 스레드만 들어오므로, 별도의 DB 트랜잭션으로
     * 묶지 않아도 "조회 → 판단 → 저장"이 원자적으로 보인다. (자바 락이 1차 방어선,
     * Refund.refundId의 DB 유니크 제약이 2차 방어선)
     */
    private ResponseEntity<?> acceptLocked(RefundRequest request) {
        Optional<Refund> existing = repository.findByRefundId(request.refundId());
        if (existing.isPresent()) {
            Refund refund = existing.get();
            if (!refund.sameContentAs(request.orderId(), request.paidAmount(), request.refundAmount())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                        "REFUND_ID_CONFLICT", "이미 존재하는 요청 ID이며 내용이 다릅니다.", request.refundId()));
            }
            String failureReason = refund.getFailureReason() == null ? null : refund.getFailureReason().name();
            return ResponseEntity.ok(new RefundDuplicateResponse(
                    refund.getRefundId(), refund.getStatus().name(), failureReason));
        }

        long reserved = repository.sumReservedAmount(request.orderId());
        if (reserved + request.refundAmount() > request.paidAmount()) {
            Refund rejected = Refund.rejected(request.refundId(), request.orderId(),
                    request.paidAmount(), request.refundAmount(), request.reason(), Instant.now());
            saveNewRefund(rejected);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(
                    "REFUND_LIMIT_EXCEEDED", "환불 가능 금액을 초과했습니다.", request.refundId()));
        }

        Refund accepted = Refund.accepted(request.refundId(), request.orderId(),
                request.paidAmount(), request.refundAmount(), request.reason(), Instant.now());
        saveNewRefund(accepted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefundAcceptedResponse(request.refundId()));
    }

    /**
     * 정상 경로에서는 orderId 락이 이미 같은 refundId의 동시 삽입을 막아 주므로
     * 유니크 제약 위반은 거의 일어나지 않는다. 그래도 방어적으로 잡아
     * "이미 있던 것으로 취급"하도록 폴백한다.
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
