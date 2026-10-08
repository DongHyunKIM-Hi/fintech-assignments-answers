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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 결제 승인·취소·한도 조회. 이 클래스가 동시성을 책임지는 지점입니다.
 *
 * 핵심 아이디어 (가이드 12장 힌트, docs/answer.md에 이유를 정리):
 * 1) 결제 ID 하나마다 모니터 락(paymentLocks)을 두고, "이미 있는지 확인 → 없으면 처리"를
 *    이 락 안에서 통째로 수행한다. 락 키가 사용자와 무관하게 결제 ID 자체이므로,
 *    같은 결제 ID를 같은 사용자든 다른 사용자든 동시에 보내도 한 스레드만 통과한다. (S3, S4)
 *    거절(422)로 끝나 저장을 하지 않으면 락을 빠져나가는 것만으로 그 ID는 자동으로 "미사용"
 *    상태로 남는다 — 별도로 선점을 해제하는 코드가 필요 없다.
 * 2) 사용자 한도(사용액 읽기 → 판단 → 쓰기)는 사용자 하나마다 락(userLocks)을 두어 보호한다.
 *    서로 다른 사용자는 완전히 병렬로 처리된다. (S1, S5)
 * 3) 잠금 순서는 항상 paymentLock → userLock 한 방향이라 교착 상태가 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long DAILY_LIMIT = 1_000_000L;

    private final LimitStore limitStore;
    private final FeePolicy feePolicy;

    /** 결제 ID별 락. 사용자와 무관하게 결제 ID 전역에서 유일함을 보장한다. */
    private final ConcurrentHashMap<String, Object> paymentLocks = new ConcurrentHashMap<>();
    /** 사용자 ID별 락. 한도 사용액을 읽고-판단하고-쓰는 구간을 보호한다. */
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public PaymentApprovedResponse approve(PaymentRequest request) {
        Object paymentLock = paymentLocks.computeIfAbsent(request.getPaymentId(), id -> new Object());
        synchronized (paymentLock) {
            if (limitStore.findPayment(request.getPaymentId()).isPresent()) {
                // 이미 있는 기록이면 승인이었든 취소였든(재사용 불가, D3) 중복입니다.
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_PAYMENT,
                        "이미 존재하는 결제 ID입니다.", request.getPaymentId());
            }

            Object userLock = userLocks.computeIfAbsent(request.getUserId(), id -> new Object());
            synchronized (userLock) {
                long used = limitStore.getUsedAmount(request.getUserId());
                long newUsed = used + request.getAmount();
                if (newUsed > DAILY_LIMIT) {
                    // 저장을 하지 않고 그냥 락을 빠져나가면, 이 paymentId는 다시 요청할 수 있습니다.
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
        }
    }

    public CancelResponse cancel(String paymentId) {
        Object paymentLock = paymentLocks.computeIfAbsent(paymentId, id -> new Object());
        synchronized (paymentLock) {
            PaymentRecord record = limitStore.findPayment(paymentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                            "결제를 찾을 수 없습니다.", paymentId));

            if (record.getState() == PaymentState.CANCELED) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_CANCELED,
                        "이미 취소된 결제입니다.", paymentId);
            }

            Object userLock = userLocks.computeIfAbsent(record.getUserId(), id -> new Object());
            synchronized (userLock) {
                long used = limitStore.getUsedAmount(record.getUserId());
                long restored = used - record.getAmount();
                limitStore.setUsedAmount(record.getUserId(), restored);
                limitStore.savePayment(record.withState(PaymentState.CANCELED));

                return CancelResponse.canceled(paymentId, record.getAmount(), DAILY_LIMIT - restored);
            }
        }
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
