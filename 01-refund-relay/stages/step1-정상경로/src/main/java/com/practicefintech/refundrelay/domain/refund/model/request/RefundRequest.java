package com.practicefintech.refundrelay.domain.refund.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환불 접수 요청 본문.
 *
 * 1단계에서 일부러 하지 않는 것
 * - 입력 검증 애너테이션(@NotBlank, @Min, @Size)을 붙이지 않는다.
 *   금액이 0이어도, 필수값이 없어도 그대로 저장을 시도한다.
 *
 * 왜 다음 단계가 필요한가
 * - 2단계에서 검증 애너테이션과 @Valid를 붙여 400 INVALID_REQUEST를 응답한다.
 */
@Getter
@NoArgsConstructor
public class RefundRequest {

    private String refundId;

    private String orderId;

    private long paidAmount;

    private long refundAmount;

    private String reason;
}
