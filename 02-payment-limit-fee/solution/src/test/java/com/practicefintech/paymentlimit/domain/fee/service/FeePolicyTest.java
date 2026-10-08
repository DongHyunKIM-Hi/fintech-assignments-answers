package com.practicefintech.paymentlimit.domain.fee.service;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 기준표 B(테스트 작성) "상" 수준의 예시입니다.
 * 규칙이 바뀌는 경계값과 규칙끼리 겹치는 경우를 표에서 그대로 옮겨 검증합니다.
 */
class FeePolicyTest {

    private final FeePolicy feePolicy = new FeePolicy();

    @ParameterizedTest(name = "{0} {1} {2}원 -> {3}원")
    @CsvSource({
            // 면제 경계 (R1)
            "BASIC, DOMESTIC, 999, 0",
            "BASIC, DOMESTIC, 1000, 100",
            // 절사 (R9)
            "BASIC, DOMESTIC, 12350, 123",
            "BASIC, DOMESTIC, 12349, 123",
            // 최소 수수료 (R9)
            "GOLD, DOMESTIC, 5000, 100",
            // 고액 감면 경계 (R8): 999,999원은 미적용, 1,000,000원부터 적용
            "BASIC, DOMESTIC, 999999, 9999",
            "BASIC, DOMESTIC, 1000000, 8000",
            "BASIC, DOMESTIC, 1000001, 8000",
            // 등급별 수수료율 (R3~R7)
            "SILVER, DOMESTIC, 100000, 800",
            "GOLD, DOMESTIC, 100000, 500",
            "SILVER, OVERSEAS, 100000, 2400",
            "GOLD, OVERSEAS, 100000, 1500",
            // VIP 면제와 예외 (R2, R6)
            "VIP, DOMESTIC, 5000000, 0",
            "VIP, DOMESTIC, 500, 0",
            "VIP, OVERSEAS, 999, 0",
            "VIP, OVERSEAS, 100000, 1500",
            // 상한 (R10)
            "BASIC, OVERSEAS, 10000000, 50000",
            "BASIC, OVERSEAS, 100000000, 50000",
            // 우선순위 조합: 저액 VIP 해외는 R1(면제)이 R6보다 먼저
            "VIP, OVERSEAS, 500, 0",
    })
    void calculatesFeeAccordingToRuleTable(Grade grade, PayType payType, long amount, long expectedFee) {
        assertEquals(expectedFee, feePolicy.calculate(grade, payType, amount));
    }
}
