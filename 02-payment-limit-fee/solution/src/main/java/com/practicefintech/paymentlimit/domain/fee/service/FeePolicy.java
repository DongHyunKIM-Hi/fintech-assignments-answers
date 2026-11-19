package com.practicefintech.paymentlimit.domain.fee.service;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 수수료 정책. (지원자 README 6장 규칙 표의 정답 구현)
 *
 * 기존 코드({@code LegacyFeeCalculator})를 규칙 표 기준으로 다시 작성했습니다.
 * bp(1bp = 0.01%) 단위 정수로만 계산해 오차가 없습니다.
 * 표의 적용 순서(면제, 수수료율 결정, 고액 감면, 버림과 최소 수수료, 최대 수수료)대로 단계를 나누었습니다.
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
        // [순서 1] 면제 확인 (R1 소액, R2 VIP 국내) — 면제면 여기서 0원으로 끝나므로
        //          아래의 고액 감면·최소 수수료가 면제를 건드릴 일이 없다.
        if (isExempt(grade, payType, amount)) {
            return 0;
        }
        // [순서 2] 수수료율 결정 — 등급×유형 표에서 bp를 꺼낸다. R3~R7 다섯 규칙이 이 한 줄이다.
        long bp = BASE_RATE_BP.get(grade).get(payType);
        // [순서 3] 고액 감면 (R8) — 1,000,000원 "이상"이면 수수료율에서 0.20%p(=20bp)를 뺀다.
        //          %p는 비율이 아니라 값 자체를 빼는 것이라 bp 정수 뺄셈 한 번이면 된다.
        //          경계를 초과(>)로 쓰면 딱 1,000,000원에서 감면이 빠져 계산 예시(8,000원)와 어긋난다.
        if (amount >= HIGH_VALUE_THRESHOLD) {
            bp -= HIGH_VALUE_DISCOUNT_BP;
        }
        // [순서 4] 금액×bp÷10000 — 정수 나눗셈이라 원 단위 미만 절사(R9)가 저절로 된다.
        //          double로 계산하면 1,000,000원 × 2.80%가 28,000이 아니라 27,999가 되는 오차가 생긴다.
        long raw = (amount * bp) / BP_DENOMINATOR;
        long withMinimum = Math.max(raw, MINIMUM_FEE); // 면제가 아니면 최소 100원 (R9)
        // [순서 5] 최대 수수료 50,000원 (R10)
        return Math.min(withMinimum, MAXIMUM_FEE);
    }

    private boolean isExempt(Grade grade, PayType payType, long amount) {
        if (amount < EXEMPT_AMOUNT_THRESHOLD) { // R1
            return true;
        }
        return grade == Grade.VIP && payType == PayType.DOMESTIC; // R2
    }
}
