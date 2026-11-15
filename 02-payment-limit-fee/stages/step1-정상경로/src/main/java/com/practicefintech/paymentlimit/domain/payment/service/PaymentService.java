package com.practicefintech.paymentlimit.domain.payment.service;

import com.practicefintech.paymentlimit.common.entity.PaymentRecord;
import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import com.practicefintech.paymentlimit.common.enums.PaymentState;
import com.practicefintech.paymentlimit.domain.fee.service.FeePolicy;
import com.practicefintech.paymentlimit.domain.payment.model.request.PaymentRequest;
import com.practicefintech.paymentlimit.domain.payment.model.response.CancelResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.LimitResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.PaymentApprovedResponse;
import com.practicefintech.paymentlimit.domain.payment.repository.LimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [1단계: 정상 경로] 설계한 흐름이 그대로 도는지만 확인합니다.
 *
 * 승인: 사용액을 읽고 → 결제 금액을 더해 저장하고 → 수수료를 계산해 응답합니다.
 * 취소: 결제 기록을 찾아 → 사용액에서 빼고 → 상태를 CANCELED로 바꿔 저장합니다.
 *
 * 이 단계에서 일부러 하지 않는 것
 * - 입력 검증(400), 결제 ID 중복(409), 한도 초과(422), 없는 결제 취소(404), 이미 취소된 결제(409)  → 2단계
 * - 동시 요청 처리(락)  → 3단계
 *
 * 그래서 지금은 이런 일이 그냥 통과합니다.
 * - 같은 결제 ID로 두 번 요청하면 두 번 다 승인되고 사용액이 두 배로 늘어납니다.
 * - 한도 1,000,000원을 넘는 금액도 승인되고 잔여 한도가 음수가 됩니다.
 * - 같은 결제를 두 번 취소하면 사용액이 두 번 줄어들어 음수가 됩니다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long DAILY_LIMIT = 1_000_000L;

    private final LimitStore limitStore;
    private final FeePolicy feePolicy;

    public PaymentApprovedResponse approve(PaymentRequest request) {
        // 1) 결제 ID 중복 확인(409)은 2단계에서 추가합니다. 지금은 바로 한도부터 봅니다.
        long used = limitStore.getUsedAmount(request.getUserId());
        long newUsed = used + request.getAmount();

        // 2) 한도 초과 판정(422)도 2단계입니다.
        //    DAILY_LIMIT은 응답의 remainingLimit을 계산하는 데만 쓰고, 넘더라도 거절하지 않습니다.

        long fee = feePolicy.calculate(request.getGrade(), request.getPayType(), request.getAmount());

        // 3) 읽은 값(used)에 더해 다시 쓰는 "확인 후 실행" 구간입니다.
        //    LimitStore는 호출마다 50~150ms가 걸리므로 그 사이에 다른 요청이 끼어들 수 있지만,
        //    이 단계에서는 막지 않습니다. (3단계)
        limitStore.setUsedAmount(request.getUserId(), newUsed);
        limitStore.savePayment(new PaymentRecord(
                request.getPaymentId(), request.getUserId(), request.getAmount(), fee,
                PaymentState.APPROVED, System.currentTimeMillis()));

        return PaymentApprovedResponse.approved(
                request.getPaymentId(), request.getUserId(), request.getAmount(), fee, DAILY_LIMIT - newUsed);
    }

    public CancelResponse cancel(String paymentId) {
        // 없는 결제는 404여야 하지만(2단계), 지금은 orElseThrow가 그대로 터져 500이 됩니다.
        PaymentRecord record = limitStore.findPayment(paymentId).orElseThrow();

        // 이미 취소된 결제인지 확인하지 않습니다(409는 2단계). 두 번 취소하면 사용액이 두 번 줄어듭니다.
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
}
