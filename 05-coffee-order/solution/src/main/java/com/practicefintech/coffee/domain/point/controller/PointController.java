package com.practicefintech.coffee.domain.point.controller;

import com.practicefintech.coffee.domain.point.model.request.ChargePointRequest;
import com.practicefintech.coffee.domain.point.model.response.PointResponse;
import com.practicefintech.coffee.domain.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @PostMapping("/charge")
    public PointResponse charge(@Valid @RequestBody ChargePointRequest request) {
        return pointService.charge(request.getUserId(), request.getAmount());
    }

    /** 요구사항에는 없지만, 충전과 주문 결과를 확인하려고 추가한 조회 API */
    @GetMapping("/{userId}")
    public PointResponse getPoint(@PathVariable Long userId) {
        return pointService.getPoint(userId);
    }
}
