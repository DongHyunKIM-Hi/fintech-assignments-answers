package com.practicefintech.paymentlimit.domain.payment.service;

import com.practicefintech.paymentlimit.common.entity.PaymentRecord;
import com.practicefintech.paymentlimit.common.enums.ErrorCode;
import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import com.practicefintech.paymentlimit.common.enums.PaymentState;
import com.practicefintech.paymentlimit.common.exception.ApiException;
import com.practicefintech.paymentlimit.domain.fee.service.FeePolicy;
import com.practicefintech.paymentlimit.domain.payment.model.request.PaymentRequest;
import com.practicefintech.paymentlimit.domain.payment.model.response.CancelResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.LimitResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.PaymentApprovedResponse;
import com.practicefintech.paymentlimit.domain.payment.repository.LimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * [2단계: 예외 처리] API 명세의 모든 상황을 상태 코드로 구분해 응답합니다.
 *
 * 1단계에 더해 추가한 것
 * - 결제 ID 중복 → 409 DUPLICATE_PAYMENT (취소된 결제 ID도 재사용 불가)
 * - 한도 초과 → 422 LIMIT_EXCEEDED (거절한 결제는 저장하지 않으므로 같은 ID로 다시 요청 가능)
 * - 없는 결제 취소 → 404 PAYMENT_NOT_FOUND, 이미 취소된 결제 → 409 ALREADY_CANCELED
 * - 승인 요청 검사 순서: 입력 검증(400) → 중복(409) → 한도(422) → 승인(200)
 *
 * 이 단계에서 일부러 하지 않는 것: 동시 요청 처리 (3단계 = {@code ../solution/})
 *
 * 왜 아직 깨지는가
 * - 승인: "결제 ID가 있는지 확인" 다음 줄에서 저장하기까지, 그리고 "사용액을 읽고 → 한도를 판단하고 → 쓰기"까지
 *   사이사이에 다른 요청이 끼어들 수 있습니다. 두 요청이 동시에 "아직 없다", "아직 여유가 있다"고 판단하면
 *   같은 결제 ID가 두 번 승인되거나 사용액이 한도를 넘습니다.
 * - 취소: "취소 상태인지 확인"과 "사용액 빼기" 사이에 같은 일이 생겨 한도가 두 번 복원됩니다.
 * - {@link LimitStore}는 호출마다 50~150ms가 걸려서 이 틈이 눈에 보일 만큼 벌어집니다.
 *   저장소 자체는 스레드 안전하지만, 여러 호출을 묶은 흐름까지 지켜 주지는 않습니다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long DAILY_LIMIT = 1_000_000L;

    private final LimitStore limitStore;
    private final FeePolicy feePolicy;

    public PaymentApprovedResponse approve(PaymentRequest request) {
        // (1) 결제 ID 중복 확인 — 이 확인과 아래 savePayment 사이가 비어 있습니다. (3단계에서 락으로 묶습니다)
        if (limitStore.findPayment(request.getPaymentId()).isPresent()) {
            // 승인이었든 취소였든 기록이 남아 있으면 중복입니다.
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_PAYMENT,
                    "이미 존재하는 결제 ID입니다.", request.getPaymentId());
        }

        // (2) 한도 확인 — 읽기, 판단, 쓰기가 떨어져 있는 "확인 후 실행" 구간입니다. (3단계에서 락으로 묶습니다)
        long used = limitStore.getUsedAmount(request.getUserId());
        long newUsed = used + request.getAmount();
        if (newUsed > DAILY_LIMIT) {
            // 거절할 때는 아무것도 저장하지 않습니다. 그래서 이 결제 ID는 다시 쓸 수 있습니다.
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.LIMIT_EXCEEDED,
                    "한도를 초과했습니다.", request.getPaymentId());
        }

        long fee = feePolicy.calculate(request.getGrade(), request.getPayType(), request.getAmount());
        limitStore.setUsedAmount(request.getUserId(), newUsed);
        limitStore.savePayment(new PaymentRecord(
                request.getPaymentId(), request.getUserId(), request.getAmount(), fee,
                PaymentState.APPROVED, System.currentTimeMillis()));

        return PaymentApprovedResponse.approved(
                request.getPaymentId(), request.getUserId(), request.getAmount(), fee, DAILY_LIMIT - newUsed);
    }

    public CancelResponse cancel(String paymentId) {
        PaymentRecord record = limitStore.findPayment(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "결제를 찾을 수 없습니다.", paymentId));

        if (record.getState() == PaymentState.CANCELED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_CANCELED,
                    "이미 취소된 결제입니다.", paymentId);
        }

        // 위 확인과 아래 사용액 변경 사이에 같은 취소 요청이 끼어들 수 있습니다. (3단계)
        long used = limitStore.getUsedAmount(record.getUserId());
        long restored = used - record.getAmount();
        limitStore.setUsedAmount(record.getUserId(), restored);
        limitStore.savePayment(record.withState(PaymentState.CANCELED));

        return CancelResponse.canceled(paymentId, record.getAmount(), DAILY_LIMIT - restored);
    }

    public LimitResponse getLimit(String userId) {
        long used = limitStore.getUsedAmount(userId);
        return new LimitResponse(userId, DAILY_LIMIT, used, DAILY_LIMIT - used);
    }

    public long getFee(Grade grade, PayType payType, long amount) {
        return feePolicy.calculate(grade, payType, amount);
    }

    /** 결제 기록을 직접 들여다볼 필요가 있을 때(테스트 등) 사용. */
    public Optional<PaymentRecord> findPayment(String paymentId) {
        return limitStore.findPayment(paymentId);
    }
}
