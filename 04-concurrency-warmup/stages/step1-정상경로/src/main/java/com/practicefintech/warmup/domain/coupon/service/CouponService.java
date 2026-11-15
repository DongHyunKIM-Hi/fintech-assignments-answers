package com.practicefintech.warmup.domain.coupon.service;

import com.practicefintech.warmup.common.entity.Coupon;
import com.practicefintech.warmup.common.enums.CouponType;
import com.practicefintech.warmup.domain.coupon.model.response.UseCouponResponse;
import com.practicefintech.warmup.domain.coupon.repository.CouponStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [1단계: 정상 경로] 설계한 흐름이 그대로 도는지만 확인합니다.
 *
 * 이 단계에서 일부러 하지 않는 것
 * - 입력 검증과 에러 응답 (2단계)
 * - 동시성 제어 (3단계)
 *
 * 지금은 "쿠폰을 발급하고, 가장 큰 할인 쿠폰을 골라 쓰고, 지운다"는 뼈대만 세웁니다.
 */
@Service
public class CouponService {

    private final CouponStore couponStore;

    public CouponService(CouponStore couponStore) {
        this.couponStore = couponStore;
    }

    public void issue(String userId, CouponType type) {
        couponStore.save(userId, Coupon.issue(type));
    }

    public UseCouponResponse use(String userId, long amount) {
        List<Coupon> coupons = couponStore.findByUserId(userId);

        // 할인액이 가장 큰 쿠폰 1장을 고른다.
        Coupon best = coupons.stream()
                .max((a, b) -> Long.compare(a.getType().discountFor(amount), b.getType().discountFor(amount)))
                .orElseThrow();   // 쿠폰이 없는 경우는 2단계에서 다룬다.

        couponStore.delete(userId, best.getId());

        long discount = best.getType().discountFor(amount);
        return new UseCouponResponse(amount, discount, amount - discount, best.getType().name());
    }
}
