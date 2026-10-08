package com.practicefintech.refundrelay.domain.refund.controller;

import com.practicefintech.refundrelay.domain.refund.model.request.RefundRequest;
import com.practicefintech.refundrelay.domain.refund.model.response.RefundListResponse;
import com.practicefintech.refundrelay.domain.refund.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 계약: 수강생 README 5장 "약속 1 — 여러분의 서버 API" */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
@Validated
public class RefundController {

    private final RefundService service;

    @PostMapping
    public ResponseEntity<?> accept(@Valid @RequestBody RefundRequest request) {
        return service.accept(request);
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<?> getOne(@PathVariable String refundId) {
        return service.getOne(refundId);
    }

    @GetMapping
    public RefundListResponse list(
            @RequestParam @NotBlank String orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(orderId, page, size);
    }
}
