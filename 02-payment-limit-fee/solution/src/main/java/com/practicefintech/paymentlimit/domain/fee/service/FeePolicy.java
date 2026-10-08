package com.practicefintech.paymentlimit.domain.fee.service;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 수수료 정책. (학생 가이드 6장 규칙 표의 정답 구현)
 *
 * 레거시 코드({@code LegacyFeeCalculator}에 해당)를 표를 기준으로 다시 작성했습니다.
 * bp(1bp = 0.01%) 정수 계산만 사용해 오차가 없고, 표의 적용 순서(면제 → 수수료율 결정 →
 * 고액 감면 → 절사·최소 → 상한)를 그대로 단계로 나누었습니다.
 */
@Component
public class FeePolicy {

    private static final long EXEMPT_AMOUNT_THRESHOLD = 1_000; // R1
    private static final long HIGH_VALUE_THRESHOLD = 1_000_000; // R8 경계 (이상)
    private static final long HIGH_VALUE_DISCOUNT_BP = 20; // R8
    private static final long MINIMUM_FEE = 100; // R9
    private static final long MAXIMUM_FEE = 50_000; // R10
    private static final long BP_DENOMINATOR = 10_000; // 1bp = 1/10000

    /** 등급 × 결제유형의 기본 수수료율(bp). VIP 국내는 R2로 먼저 면제되므로 이 표의 값은 쓰이지 않는다. */
    private static final Map<Grade, Map<PayType, Long>> BASE_RATE_BP = Map.of(
            Grade.BASIC, Map.of(PayType.DOMESTIC, 100L, PayType.OVERSEAS, 300L),
            Grade.SILVER, Map.of(PayType.DOMESTIC, 80L, PayType.OVERSEAS, 240L),
            Grade.GOLD, Map.of(PayType.DOMESTIC, 50L, PayType.OVERSEAS, 150L),
            Grade.VIP, Map.of(PayType.DOMESTIC, 0L, PayType.OVERSEAS, 150L)
    );

    public long calculate(Grade grade, PayType payType, long amount) {
        if (isExempt(grade, payType, amount)) {
            return 0;
        }
        long bp = BASE_RATE_BP.get(grade).get(payType);
        if (amount >= HIGH_VALUE_THRESHOLD) {
            bp -= HIGH_VALUE_DISCOUNT_BP;
        }
        long raw = (amount * bp) / BP_DENOMINATOR; // 정수 나눗셈 = 절사(R9)
        long withMinimum = Math.max(raw, MINIMUM_FEE); // R9
        return Math.min(withMinimum, MAXIMUM_FEE); // R10
    }

    private boolean isExempt(Grade grade, PayType payType, long amount) {
        if (amount < EXEMPT_AMOUNT_THRESHOLD) { // R1
            return true;
        }
        return grade == Grade.VIP && payType == PayType.DOMESTIC; // R2
    }
}
