package com.practicefintech.warmup.domain.coupon.service;

import com.practicefintech.warmup.common.entity.Coupon;
import com.practicefintech.warmup.common.enums.CouponType;
import com.practicefintech.warmup.common.enums.ErrorCode;
import com.practicefintech.warmup.common.exception.ApiException;
import com.practicefintech.warmup.domain.coupon.model.response.UseCouponResponse;
import com.practicefintech.warmup.domain.coupon.repository.CouponStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * [2단계: 예외 처리] 입력 검증과 에러 응답을 채웁니다. 아직 동시성 제어는 없습니다.
 *
 * 이 단계에서 되는 것 — S1(가장 큰 할인), S4(쿠폰 없음 404), amount 검증(400)
 * 이 단계에서 여전히 깨지는 것 — S2, S3(동시 사용). 3단계에서 락 범위를 설계해 고칩니다.
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
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "amount는 1 이상이어야 합니다.");
        }

        List<Coupon> coupons = couponStore.findByUserId(userId);

        Optional<Coupon> best = pickBestCoupon(coupons, amount);
        if (best.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NO_COUPON_AVAILABLE, "사용할 수 있는 쿠폰이 없습니다.");
        }

        Coupon coupon = best.get();
        // 여기서 "찾기"와 "삭제" 사이에 다른 요청이 끼어들 수 있다 -> 3단계에서 해결한다.
        couponStore.delete(userId, coupon.getId());

        long discount = coupon.getType().discountFor(amount);
        long finalAmount = amount - discount;
        return new UseCouponResponse(amount, discount, finalAmount, coupon.getType().name());
    }

    private Optional<Coupon> pickBestCoupon(List<Coupon> coupons, long amount) {
        return coupons.stream()
                .max((a, b) -> Long.compare(a.getType().discountFor(amount), b.getType().discountFor(amount)));
    }
}
