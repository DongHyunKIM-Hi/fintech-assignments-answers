package com.practicefintech.warmup.coupon;

import com.practicefintech.warmup.common.ApiException;
import com.practicefintech.warmup.common.ErrorCode;
import com.practicefintech.warmup.coupon.dto.UseCouponResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 쿠폰 발급·사용의 동시성을 책임지는 서비스.
 *
 * 락의 폭: 사용자 단위. {@link #userLocks}는 사용자 ID별로 서로 다른 락 객체를 준다.
 * 그래서 같은 사용자의 요청은 한 번에 하나씩만 처리되지만, 다른 사용자끼리는 완전히 병렬로
 * 처리된다 (T4, 시나리오 S5).
 */
@Service
public class CouponService {

    private final CouponStore couponStore;
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

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

        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            // T1 대응: 저장소가 내부 리스트 참조를 그대로 주므로, 락 안에서 즉시 복사본을 만든다.
            // 이 복사가 없으면 아래에서 훑는 도중 다른 스레드의 delete가 같은 리스트를 바꿔
            // ConcurrentModificationException이나 잘못된 결과로 이어질 수 있다.
            List<Coupon> snapshot = List.copyOf(couponStore.findByUserId(userId));

            Optional<Coupon> best = pickBestCoupon(snapshot, amount);
            if (best.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NO_COUPON_AVAILABLE, "사용할 수 있는 쿠폰이 없습니다.");
            }

            Coupon coupon = best.get();
            // T2 대응: "가장 큰 할인 찾기"와 "삭제"를 같은 락 안에서 함께 수행해,
            // 그 사이 다른 요청이 같은 쿠폰을 고르지 못하게 한다.
            couponStore.delete(userId, coupon.id());

            long discount = coupon.type().discountFor(amount);
            long finalAmount = amount - discount;
            return new UseCouponResponse(amount, discount, finalAmount, coupon.type().name());
        }
    }

    private Optional<Coupon> pickBestCoupon(List<Coupon> coupons, long amount) {
        return coupons.stream()
                .max((a, b) -> Long.compare(a.type().discountFor(amount), b.type().discountFor(amount)));
    }
}
