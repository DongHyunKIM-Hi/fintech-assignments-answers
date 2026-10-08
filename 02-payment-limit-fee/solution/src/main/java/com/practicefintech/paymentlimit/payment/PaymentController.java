package com.practicefintech.paymentlimit.payment;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 결제 승인·취소·한도 조회·수수료 계산 API. 실제 로직은 {@link PaymentService}, {@link com.practicefintech.paymentlimit.fee.FeePolicy}에 있다. */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/payments")
    public PaymentApprovedResponse approve(@Valid @RequestBody PaymentRequest request) {
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
        if (amount < 1 || amount > 100_000_000) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_REQUEST, "amount는 1 이상 100,000,000 이하여야 합니다.", null);
        }
        long fee = paymentService.getFee(grade, payType, amount);
        return new FeeQueryResponse(grade, payType, amount, fee);
    }
}
