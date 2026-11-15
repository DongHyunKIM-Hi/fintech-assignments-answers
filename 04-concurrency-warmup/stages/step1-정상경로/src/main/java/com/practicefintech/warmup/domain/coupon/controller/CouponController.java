package com.practicefintech.warmup.domain.coupon.controller;

import com.practicefintech.warmup.common.enums.CouponType;
import com.practicefintech.warmup.domain.coupon.model.request.IssueCouponRequest;
import com.practicefintech.warmup.domain.coupon.model.request.UseCouponRequest;
import com.practicefintech.warmup.domain.coupon.model.response.CouponView;
import com.practicefintech.warmup.domain.coupon.model.response.UseCouponResponse;
import com.practicefintech.warmup.domain.coupon.repository.CouponStore;
import com.practicefintech.warmup.domain.coupon.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/coupons")
public class CouponController {

    private final CouponStore couponStore;
    private final CouponService couponService;

    public CouponController(CouponStore couponStore, CouponService couponService) {
        this.couponStore = couponStore;
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<Void> issue(@PathVariable String userId, @RequestBody IssueCouponRequest request) {
        CouponType type = parseType(request.getType());
        couponService.issue(userId, type);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<CouponView> list(@PathVariable String userId) {
        return couponStore.findByUserId(userId).stream()
                .map(c -> new CouponView(c.getId(), c.getType().name()))
                .toList();
    }

    @PostMapping("/use")
    public UseCouponResponse use(@PathVariable String userId, @RequestBody UseCouponRequest request) {
        return couponService.use(userId, request.getAmount());
    }

    private CouponType parseType(String type) {
        // 1단계는 올바른 값만 들어온다고 보고 그대로 변환한다. 검증은 2단계에서 추가한다.
        return CouponType.valueOf(type);
    }
}
