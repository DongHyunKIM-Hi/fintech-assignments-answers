package com.practicefintech.paymentlimit.domain.payment.service;

import com.practicefintech.paymentlimit.common.enums.ErrorCode;
import com.practicefintech.paymentlimit.common.enums.Grade;
import com.practicefintech.paymentlimit.common.enums.PayType;
import com.practicefintech.paymentlimit.common.exception.ApiException;
import com.practicefintech.paymentlimit.domain.fee.service.FeePolicy;
import com.practicefintech.paymentlimit.domain.payment.model.request.PaymentRequest;
import com.practicefintech.paymentlimit.domain.payment.model.response.PaymentApprovedResponse;
import com.practicefintech.paymentlimit.domain.payment.repository.LimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기준표 B(테스트 작성) "상" 수준의 예시입니다.
 * 요청을 실제로 동시에 출발시키고(래치), 성공 개수와 최종 사용액(불변 조건)을 함께 검증합니다.
 * 매 테스트마다 새 LimitStore를 만들어 서로 영향을 주지 않습니다(재현성·독립성).
 */
class PaymentServiceConcurrencyTest {

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(new LimitStore(), new FeePolicy());
    }

    /** S3: 같은 결제 ID(같은 사용자) 요청이 동시에 여러 건 오면 정확히 1건만 성공한다. */
    @Test
    void sameUserDuplicatePaymentId_onlyOneSucceeds() throws InterruptedException {
        Result result = fireConcurrently(10, i -> approveOutcome("P-DUP-USER", "U-1", 1000));

        assertEquals(1, result.successCount());
        assertEquals(9, result.conflictCount());
        assertEquals(1000, service.getLimit("U-1").getUsedAmount());
    }

    /** S4: 같은 결제 ID를 서로 다른 사용자가 동시에 보내도 정확히 1건만 성공한다. */
    @Test
    void differentUsersSamePaymentId_onlyOneSucceeds() throws InterruptedException {
        int attempts = 10;
        Result result = fireConcurrently(attempts, i -> approveOutcome("P-DUP-GLOBAL", "U-" + i, 1000));

        assertEquals(1, result.successCount());
        assertEquals(9, result.conflictCount());

        long totalUsed = 0;
        for (int i = 0; i < attempts; i++) {
            totalUsed += service.getLimit("U-" + i).getUsedAmount();
        }
        assertEquals(1000, totalUsed);
    }

    /** S5: 같은 사용자의 서로 다른 결제가 동시에 한도를 넘는 합으로 오면, 한도 안에서만 승인되고 사용액은 정확하다. */
    @Test
    void sameUserConcurrentPayments_neverExceedsLimit() throws InterruptedException {
        long amount = 400_000; // 3건이면 합계 1,200,000 > 1,000,000
        Result result = fireConcurrently(3, i -> approveOutcome("P-LIMIT-" + i, "U-LIMIT", amount));

        assertEquals(2, result.successCount());
        assertEquals(1, result.rejectedCount());
        long used = service.getLimit("U-LIMIT").getUsedAmount();
        assertEquals(800_000, used);
        assertTrue(used <= 1_000_000, "사용액이 한도를 넘으면 안 됩니다");
    }

    /** S9: 같은 결제의 취소가 동시에 여러 건 오면 정확히 1건만 성공하고, 한도는 한 번만 복원된다. */
    @Test
    void concurrentCancelOfSamePayment_onlyOneSucceeds() throws InterruptedException {
        approve("P-CANCEL", "U-CANCEL", 10_000);
        assertEquals(10_000, service.getLimit("U-CANCEL").getUsedAmount());

        Result result = fireConcurrently(10, i -> {
            try {
                service.cancel("P-CANCEL");
                return Outcome.SUCCESS;
            } catch (ApiException e) {
                return Outcome.CONFLICT;
            }
        });

        assertEquals(1, result.successCount());
        assertEquals(9, result.conflictCount());
        assertEquals(0, service.getLimit("U-CANCEL").getUsedAmount());
    }

    /** S11: 기존 결제의 취소와 새 결제의 승인이 동시에 와도, 최종 사용액은 항상 불변 조건을 지킨다. */
    @Test
    void concurrentCancelAndApprove_keepsInvariant() throws Exception {
        approve("P-MIX-A", "U-MIX", 900_000);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Outcome> cancelTask = () -> {
            ready.countDown();
            go.await();
            service.cancel("P-MIX-A");
            return Outcome.SUCCESS;
        };
        Callable<Outcome> approveTask = () -> {
            ready.countDown();
            go.await();
            return approveOutcome("P-MIX-B", "U-MIX", 200_000);
        };

        Future<Outcome> cancelFuture = pool.submit(cancelTask);
        Future<Outcome> approveFuture = pool.submit(approveTask);
        ready.await();
        go.countDown();
        cancelFuture.get(10, TimeUnit.SECONDS);
        Outcome approveOutcome = approveFuture.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        long used = service.getLimit("U-MIX").getUsedAmount();
        // 어느 순서로 처리되든, 취소되지 않은 승인 결제의 합과 같아야 한다 (0 또는 200,000).
        long expected = approveOutcome == Outcome.SUCCESS ? 200_000 : 0;
        assertEquals(expected, used);
        assertTrue(used >= 0 && used <= 1_000_000);
    }

    // ---- 테스트 보조 코드 ----

    private enum Outcome { SUCCESS, CONFLICT, REJECTED }

    private record Result(int successCount, int conflictCount, int rejectedCount) {
    }

    private PaymentApprovedResponse approve(String paymentId, String userId, long amount) {
        return service.approve(new PaymentRequest(paymentId, userId, amount, Grade.BASIC, PayType.DOMESTIC));
    }

    private Outcome approveOutcome(String paymentId, String userId, long amount) {
        try {
            approve(paymentId, userId, amount);
            return Outcome.SUCCESS;
        } catch (ApiException e) {
            return e.getCode() == ErrorCode.LIMIT_EXCEEDED ? Outcome.REJECTED : Outcome.CONFLICT;
        }
    }

    private Result fireConcurrently(int count, IntFunction<Outcome> action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                switch (action.apply(idx)) {
                    case SUCCESS -> success.incrementAndGet();
                    case CONFLICT -> conflict.incrementAndGet();
                    case REJECTED -> rejected.incrementAndGet();
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
        return new Result(success.get(), conflict.get(), rejected.get());
    }
}
