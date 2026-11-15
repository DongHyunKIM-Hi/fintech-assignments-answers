package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.domain.refund.model.request.RefundRequest;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundAcceptedResponse;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundDetailResponse;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 접수 로직. 1단계는 "받으면 저장하고 바로 202"가 전부다.
 * 중요한 것은 여기서 결제대행사를 부르지 않는다는 점이다(접수와 처리의 분리).
 *
 * 1단계에서 일부러 하지 않는 것
 * - 같은 환불 ID로 다시 들어온 요청을 가려내지 않는다(S2 200 duplicate / S3 409 충돌).
 * - 환불 한도를 계산하지 않는다(S5 422 REJECTED).
 * - 없는 환불 ID를 조회해도 404를 주지 않는다.
 * - 같은 주문에 동시에 들어온 요청을 묶어 주는 락이 없다(S4, S6).
 *
 * 왜 다음 단계가 필요한가
 * - 2단계: 중복/충돌/한도/404를 규칙대로 응답한다.
 * - 3단계: 2단계의 "조회하고 없으면 저장한다"는 순서는 같은 요청이 동시에 들어오면 둘 다
 *   없다고 판단해 두 건이 저장된다. 주문 단위 락과 DB 유일 제약으로 막는다.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository repository;

    public ResponseEntity<?> accept(RefundRequest request) {
        Refund accepted = Refund.accepted(request.getRefundId(), request.getOrderId(),
                request.getPaidAmount(), request.getRefundAmount(), request.getReason(), Instant.now());
        repository.save(accepted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefundAcceptedResponse(request.getRefundId()));
    }

    public ResponseEntity<?> getOne(String refundId) {
        // 없는 환불 ID면 본문이 비어 있는 200이 나간다. 404는 2단계에서 처리한다.
        return ResponseEntity.ok(repository.findByRefundId(refundId)
                .map(RefundDetailResponse::from)
                .orElse(null));
    }
}
