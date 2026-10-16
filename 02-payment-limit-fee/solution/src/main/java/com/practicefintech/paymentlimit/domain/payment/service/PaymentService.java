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
 * 결제 승인, 취소, 한도 조회. 동시성 처리는 이 클래스에서 한다.
 * (설계 이유는 docs/answer.md 참고)
 *
 * 1) 결제 ID마다 모니터 락(paymentLocks)을 두고, 기존 결제 확인부터 처리까지 이 락 안에서 수행한다.
 *    락 기준이 사용자와 관계없는 결제 ID이므로, 같은 결제 ID를 같은 사용자나 다른 사용자가
 *    동시에 보내도 한 스레드만 통과한다. (S3, S4)
 *    한도 초과(422)로 끝나면 아무것도 저장하지 않고 락을 빠져나가므로, 그 ID는 다시 사용할 수 있다.
 *    선점을 해제하는 코드는 따로 필요 없다.
 * 2) 사용자 한도(사용액 읽기, 판단, 쓰기)는 사용자마다 락(userLocks)을 두어 보호한다.
 *    서로 다른 사용자의 결제는 병렬로 처리된다. (S1, S5)
 * 3) 락은 항상 paymentLock, userLock 순서로 걸기 때문에 교착 상태가 생기지 않는다.
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
