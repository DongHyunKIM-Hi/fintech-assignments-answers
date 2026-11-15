package com.practicefintech.paymentlimit.domain.payment.controller;

import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import com.practicefintech.paymentlimit.domain.fee.model.response.FeeQueryResponse;
import com.practicefintech.paymentlimit.domain.payment.model.request.PaymentRequest;
import com.practicefintech.paymentlimit.domain.payment.model.response.CancelResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.LimitResponse;
import com.practicefintech.paymentlimit.domain.payment.model.response.PaymentApprovedResponse;
import com.practicefintech.paymentlimit.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * [1단계: 정상 경로] 결제 승인, 취소, 한도 조회, 수수료 계산 API.
 * 경로와 메서드 시그니처는 처음부터 최종본과 같게 두고, 안의 내용만 단계마다 채워 갑니다.
 *
 * 이 단계에서 일부러 하지 않는 것
 * - {@code @Valid}를 비롯한 입력 검증(400)과 수수료 조회의 금액 범위 검사  → 2단계
 * - 에러 응답 형식 통일({@code GlobalExceptionHandler})  → 2단계
 */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/payments")
    public PaymentApprovedResponse approve(@RequestBody PaymentRequest request) {
        return paymentService.approve(request);
    }

    @PostMapping("/api/v1/payments/{paymentId}/cancel")
    public CancelResponse cancel(@PathVariable String paymentId) {
        return paymentService.cancel(paymentId);
    }

    @GetMapping("/api/v1/users/{userId}/limit")
    public LimitResponse getLimit(@PathVariable String userId) {
        return paymentService.getLimit(userId);
    }

    @GetMapping("/api/v1/fees")
    public FeeQueryResponse getFee(@RequestParam Grade grade, @RequestParam PayType payType, @RequestParam long amount) {
        // amount가 1 이상 100,000,000 이하인지 확인하는 400 처리는 2단계입니다.
        long fee = paymentService.getFee(grade, payType, amount);
        return new FeeQueryResponse(grade, payType, amount, fee);
    }
}
