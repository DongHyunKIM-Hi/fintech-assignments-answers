package com.practicefintech.refundrelay.domain.refund.model.response;

import lombok.Getter;

/** 2단계에서 추가. 같은 환불 ID, 같은 내용으로 다시 들어온 요청에 돌려주는 200 응답. */
@Getter
public class RefundDuplicateResponse {

    private final String refundId;
    private final String status;
    private final boolean duplicate;
    private final String failureReason;

    public RefundDuplicateResponse(String refundId, String status, boolean duplicate, String failureReason) {
        this.refundId = refundId;
        this.status = status;
        this.duplicate = duplicate;
        this.failureReason = failureReason;
    }

    public RefundDuplicateResponse(String refundId, String status, String failureReason) {
        this(refundId, status, true, failureReason);
    }
}
