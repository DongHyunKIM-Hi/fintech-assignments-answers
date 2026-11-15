package com.practicefintech.portfolio.common.enums;

/**
 * 1단계에서는 쓰지 않는다. CSV의 통화 칸에는 KRW, krw, 원, USD, usd, US$ 여섯 가지 표기가 섞여 있는데
 * 1단계는 읽은 문자열을 그대로 저장하고 그대로 응답한다. 이 enum으로 정규화하는 일은 2단계에서 한다.
 */
public enum CurrencyCode {
    KRW, USD
}
