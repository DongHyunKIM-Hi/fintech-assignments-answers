package com.practicefintech.paymentlimit.domain.payment.model.request;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 승인 요청. API 명세의 일부이므로 필드를 추가하거나 삭제하거나 이름을 바꾸지 않습니다.
 *
 * [1단계: 정상 경로] 필드만 두고 검증 애노테이션({@code @NotNull}, {@code @Size}, {@code @Min}, {@code @Max})은
 * 아직 붙이지 않습니다. 그래서 금액이 0이나 음수여도, 필수값이 비어 있어도 그대로 승인됩니다. (2단계에서 400으로 거절)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String paymentId;

    private String userId;

    private Long amount;

    private Grade grade;

    private PayType payType;
}
