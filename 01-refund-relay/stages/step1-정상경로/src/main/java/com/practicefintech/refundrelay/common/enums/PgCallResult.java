package com.practicefintech.refundrelay.common.enums;

/**
 * 가상 결제대행사 호출 한 번의 결과 분류.
 * 1단계는 응답 대기 시간(타임아웃)을 두지 않으므로 TIMEOUT이 없다. 2단계에서 추가한다.
 */
public enum PgCallResult {
    SUCCESS,
    DECLINED,
    ERROR
}
