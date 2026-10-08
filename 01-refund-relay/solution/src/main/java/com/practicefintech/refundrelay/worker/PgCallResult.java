package com.practicefintech.refundrelay.worker;

/** 가상 결제대행사 호출 한 번의 결과 분류. */
public enum PgCallResult {
    SUCCESS,
    DECLINED,
    ERROR,
    TIMEOUT
}
