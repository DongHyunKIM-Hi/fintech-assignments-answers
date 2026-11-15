package com.practicefintech.refundrelay.common.enums;

/** 가상 결제대행사 호출 한 번의 결과 분류. TIMEOUT은 2단계에서 추가했다. */
public enum PgCallResult {
    SUCCESS,
    DECLINED,
    ERROR,
    TIMEOUT
}
