package com.practicefintech.paymentlimit.domain.fee.service;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import org.springframework.stereotype.Component;

/**
 * [1단계: 정상 경로] 수수료 계산의 뼈대만 세웁니다.
 *
 * 지금은 기본 수수료율 1.00%(100bp) 하나만 씁니다.
 * "금액 × 수수료율"이라는 계산 한 줄이 승인 응답과 수수료 조회 응답에 똑같이 흘러가는지만 확인합니다.
 *
 * 이 단계에서 일부러 하지 않는 것 (모두 2단계에서 추가)
 * - R1 소액 면제(1,000원 미만), R2 VIP 국내 면제
 * - R3의 해외 3.00%, R4~R7 등급별 수수료율(SILVER 0.8배, GOLD 0.5배, VIP 해외 1.50%)
 * - R8 고액 감면(1,000,000원 이상 0.20%p)
 * - R9 최소 수수료 100원, R10 최대 수수료 50,000원
 *
 * 그래서 지금은 등급과 결제 유형을 받기만 하고 쓰지 않습니다.
 * 지원자 README 6장의 계산 예시 8개 중 맞는 것은 하나도 없습니다. (예: GOLD 국내 50,000원 → 500원, 표는 250원)
 */
@Component
public class FeePolicy {

    private static final long BASE_RATE_BP = 100;      // 1.00% (R3의 국내 기본 수수료율)
    private static final long BP_DENOMINATOR = 10_000; // 1bp = 1/10000

    public long calculate(Grade grade, PayType payType, long amount) {
        // 정수 나눗셈이라 원 단위 미만은 자연스럽게 버려집니다.
        return (amount * BASE_RATE_BP) / BP_DENOMINATOR;
    }
}
