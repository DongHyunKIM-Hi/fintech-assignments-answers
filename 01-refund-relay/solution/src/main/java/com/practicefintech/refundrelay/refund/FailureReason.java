package com.practicefintech.refundrelay.refund;

/** 계약에서 고정한 실패 사유 4종. */
public enum FailureReason {
    REFUND_LIMIT_EXCEEDED,
    PG_DECLINED,
    PG_ERROR,
    PG_TIMEOUT
}
