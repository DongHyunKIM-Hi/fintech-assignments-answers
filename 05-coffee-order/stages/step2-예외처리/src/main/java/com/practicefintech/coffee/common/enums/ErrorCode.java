package com.practicefintech.coffee.common.enums;

/** 3단계에서 분산 락 대기 초과를 뜻하는 REQUEST_BUSY가 더해진다. */
public enum ErrorCode {
    INVALID_REQUEST,
    MENU_NOT_FOUND,
    INSUFFICIENT_POINT,
    INTERNAL_ERROR
}
