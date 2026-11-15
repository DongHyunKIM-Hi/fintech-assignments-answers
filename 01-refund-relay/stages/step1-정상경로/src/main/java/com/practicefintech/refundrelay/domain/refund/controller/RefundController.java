package com.practicefintech.refundrelay.domain.refund.controller;

import com.practicefintech.refundrelay.domain.refund.model.request.RefundRequest;
import com.practicefintech.refundrelay.domain.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세: 지원자 README 5장 (구현할 API 명세)
 *
 * 1단계에서 일부러 하지 않는 것
 * - @Valid를 붙이지 않는다. 입력 검증(400)이 없다.
 * - 목록 조회(GET /api/v1/refunds?orderId=...)를 만들지 않는다.
 *
 * 왜 다음 단계가 필요한가
 * - 2단계에서 검증(400), 단건 조회 404, 목록 조회(페이지 나누기)를 채운다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService service;

    @PostMapping
    public ResponseEntity<?> accept(@RequestBody RefundRequest request) {
        return service.accept(request);
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<?> getOne(@PathVariable String refundId) {
        return service.getOne(refundId);
    }
}
