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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 접수 로직. 2단계에서 접수 단계의 네 가지 판단을 모두 채웠다.
 *   1) 같은 환불 ID + 같은 내용   -> 200, 기존 건의 현재 상태 (S2)
 *   2) 같은 환불 ID + 다른 내용   -> 409 REFUND_ID_CONFLICT (S3)
 *   3) 한도 초과                 -> 422 REFUND_LIMIT_EXCEEDED, REJECTED로 저장 (S5)
 *   4) 그 밖                     -> 202 ACCEPTED (S1)
 * 조회도 404(REFUND_NOT_FOUND)와 목록(페이지 나누기)을 채웠다. (S13)
 *
 * 2단계에서 여전히 하지 않는 것 (= 3단계 몫)
 * - 동시 요청 안전성. 아래 acceptLocked... 가 아니라 그냥 accept다. 락이 없다.
 *   "조회해서 없으면 저장한다"와 "합계를 구해서 비교한 뒤 저장한다"는 둘 다
 *   조회와 저장 사이에 다른 요청이 끼어들 수 있다.
 *     - S4: 같은 환불 ID가 동시에 들어오면 둘 다 "없음"으로 보고 2건이 저장된다.
 *     - S6: 같은 주문에 동시에 들어오면 둘 다 "한도 안"으로 보고 합계가 결제 금액을 넘는다.
 *
 * 왜 다음 단계가 필요한가
 * - 3단계에서 주문(orderId) 단위 락 안에 "중복 확인 -> 한도 확인 -> 저장"을 한 덩어리로 묶고,
 *   refundId의 DB 유일 제약을 마지막 방어 수단으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository repository;

    public ResponseEntity<?> accept(RefundRequest request) {
        // (1)(2) 같은 환불 ID가 이미 있는지 본다.
        Optional<Refund> existing = repository.findByRefundId(request.getRefundId());
        if (existing.isPresent()) {
            Refund refund = existing.get();
            if (!refund.sameContentAs(request.getOrderId(), request.getPaidAmount(), request.getRefundAmount())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                        "REFUND_ID_CONFLICT", "이미 존재하는 요청 ID이며 내용이 다릅니다.", request.getRefundId()));
            }
            return duplicateResponse(refund);
        }

        // (3) 한도 확인. 여기서 합계를 구한 뒤 아래에서 저장할 때까지 사이가 비어 있다. (S6가 깨지는 지점)
        long reserved = repository.sumReservedAmount(request.getOrderId());
        if (reserved + request.getRefundAmount() > request.getPaidAmount()) {
            Refund rejected = Refund.rejected(request.getRefundId(), request.getOrderId(),
                    request.getPaidAmount(), request.getRefundAmount(), request.getReason(), Instant.now());
            repository.saveAndFlush(rejected);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(
                    "REFUND_LIMIT_EXCEEDED", "환불 가능 금액을 초과했습니다.", request.getRefundId()));
        }

        // (4) 접수. 위의 조회와 이 저장 사이도 비어 있다. (S4가 깨지는 지점)
        Refund accepted = Refund.accepted(request.getRefundId(), request.getOrderId(),
                request.getPaidAmount(), request.getRefundAmount(), request.getReason(), Instant.now());
        repository.saveAndFlush(accepted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefundAcceptedResponse(request.getRefundId()));
    }

    /** 같은 내용의 재요청(S2)에 돌려주는 200 응답. 기존 건의 현재 상태를 그대로 보여 준다. */
    private ResponseEntity<?> duplicateResponse(Refund refund) {
        String failureReason = refund.getFailureReason() == null ? null : refund.getFailureReason().name();
        return ResponseEntity.ok(new RefundDuplicateResponse(
                refund.getRefundId(), refund.getStatus().name(), failureReason));
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
