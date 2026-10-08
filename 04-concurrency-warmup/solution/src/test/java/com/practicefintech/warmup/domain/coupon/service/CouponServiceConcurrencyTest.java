package com.practicefintech.warmup.domain.coupon.service;

import com.practicefintech.warmup.common.enums.CouponType;
import com.practicefintech.warmup.common.exception.ApiException;
import com.practicefintech.warmup.domain.coupon.model.response.UseCouponResponse;
import com.practicefintech.warmup.domain.coupon.repository.CouponStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 요청을 실제로 동시에 출발시켜(래치) S2, S3, S5를 검증합니다.
 * 매 테스트마다 새 CouponStore로 시작해 테스트끼리 서로 영향을 주지 않습니다.
 */
class CouponServiceConcurrencyTest {

    private CouponStore couponStore;
    private CouponService service;

    @BeforeEach
    void setUp() {
        couponStore = new CouponStore();
        service = new CouponService(couponStore);
    }

    /** S2: 쿠폰 2장 보유, 동시에 사용 요청 2건 → 2건 모두 성공, 서로 다른 쿠폰이 하나씩 소모된다. */
    @Test
    void twoCoupons_twoConcurrentUses_bothSucceed() throws InterruptedException {
        service.issue("U-1", CouponType.PERCENT_15);
        service.issue("U-1", CouponType.WON_800);

        Result result = fireConcurrently(2, i -> useOutcome("U-1", 10_000));

        assertEquals(2, result.successCount());
        assertEquals(0, result.notFoundCount());
        assertTrue(couponStore.findByUserId("U-1").isEmpty(), "쿠폰이 모두 소모되어야 합니다");
    }

    /** S3: 쿠폰 1장 보유, 동시에 사용 요청 5건 → 정확히 1건만 성공, 나머지는 쿠폰 없음. */
    @Test
    void oneCoupon_fiveConcurrentUses_onlyOneSucceeds() throws InterruptedException {
        service.issue("U-2", CouponType.PERCENT_15);

        Result result = fireConcurrently(5, i -> useOutcome("U-2", 10_000));

        assertEquals(1, result.successCount());
        assertEquals(4, result.notFoundCount());
        assertTrue(couponStore.findByUserId("U-2").isEmpty());
    }

    /** S5: 서로 다른 사용자의 사용 요청이 동시에 와도 서로 영향 없이 병렬로 처리된다. */
    @Test
    void differentUsers_concurrentUses_allSucceedIndependently() throws InterruptedException {
        int users = 8;
        for (int i = 0; i < users; i++) {
            service.issue("U-MULTI-" + i, CouponType.WON_3000);
        }

        Result result = fireConcurrently(users, i -> useOutcome("U-MULTI-" + i, 10_000));

        assertEquals(users, result.successCount());
        for (int i = 0; i < users; i++) {
            assertTrue(couponStore.findByUserId("U-MULTI-" + i).isEmpty());
        }
    }

    /** S1: 여러 장 중 할인액이 가장 큰 쿠폰이 적용되고 사라진다. */
    @Test
    void use_picksCouponWithLargestDiscount() {
        service.issue("U-3", CouponType.WON_800);   // 10,000원에 800원 할인
        service.issue("U-3", CouponType.PERCENT_5); // 10,000원에 500원 할인
        service.issue("U-3", CouponType.PERCENT_15); // 10,000원에 1,500원 할인 (최대)

        UseCouponResponse response = service.use("U-3", 10_000);

        assertEquals("PERCENT_15", response.getCouponType());
        assertEquals(1_500, response.getDiscountAmount());
        assertEquals(8_500, response.getFinalAmount());
        assertEquals(2, couponStore.findByUserId("U-3").size());
    }

    // ---- 테스트 보조 코드 ----

    private enum Outcome { SUCCESS, NOT_FOUND }

    private record Result(int successCount, int notFoundCount) {
    }

    private Outcome useOutcome(String userId, long amount) {
        try {
            service.use(userId, amount);
            return Outcome.SUCCESS;
        } catch (ApiException e) {
            return Outcome.NOT_FOUND;
        }
    }

    private Result fireConcurrently(int count, IntFunction<Outcome> action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger notFound = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (action.apply(idx) == Outcome.SUCCESS) {
                    success.incrementAndGet();
                } else {
                    notFound.incrementAndGet();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();
        return new Result(success.get(), notFound.get());
    }
}
