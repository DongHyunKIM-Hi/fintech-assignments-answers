package com.practicefintech.warmup.domain.reservation.service;

import com.practicefintech.warmup.common.exception.ApiException;
import com.practicefintech.warmup.domain.reservation.model.dto.RoomReservation;
import com.practicefintech.warmup.domain.reservation.repository.ReservationStore;
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
 * 요청을 실제로 동시에 출발시켜(래치) S8, S9, S10을 검증합니다.
 * 매 테스트마다 새 ReservationStore로 시작해 테스트끼리 서로 영향을 주지 않습니다.
 */
class ReservationServiceConcurrencyTest {

    private ReservationStore reservationStore;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservationStore = new ReservationStore();
        service = new ReservationService(reservationStore);
    }

    /** S8: 같은 방, 겹치는 시간으로 동시에 5건 예약하면 정확히 1건만 성공한다. */
    @Test
    void sameRoomOverlapping_fiveConcurrentRequests_onlyOneSucceeds() throws InterruptedException {
        Result result = fireConcurrently(5, i -> createOutcome("R1", "U-" + i, "10:00", "11:00"));

        assertEquals(1, result.successCount());
        assertEquals(4, result.conflictCount());
        assertEquals(1, reservationStore.findByRoomId("R1").size());
    }

    /** S9: 같은 방, 겹치지 않는 시간(경계만 맞닿음)은 둘 다 성공한다. */
    @Test
    void sameRoomAdjacentTimes_bothSucceed() throws InterruptedException {
        Result result = fireConcurrently(2, i -> i == 0
                ? createOutcome("R2", "U-A", "10:00", "11:00")
                : createOutcome("R2", "U-B", "11:00", "12:00"));

        assertEquals(2, result.successCount());
        assertEquals(2, reservationStore.findByRoomId("R2").size());
    }

    /** S10: 다른 방에 같은 시간으로 동시에 예약해도 서로 막히지 않고 둘 다 성공한다. */
    @Test
    void differentRooms_sameTime_bothSucceed() throws InterruptedException {
        Result result = fireConcurrently(2, i -> i == 0
                ? createOutcome("R3", "U-A", "10:00", "11:00")
                : createOutcome("R4", "U-B", "10:00", "11:00"));

        assertEquals(2, result.successCount());
        assertEquals(1, reservationStore.findByRoomId("R3").size());
        assertEquals(1, reservationStore.findByRoomId("R4").size());
    }

    /** S7: 같은 방, 겹치는 시간으로 순차 요청하면 두 번째는 거절된다. */
    @Test
    void sameRoomOverlapping_sequential_secondIsRejected() {
        service.create("R5", "U-A", "09:00", "10:00");
        try {
            service.create("R5", "U-B", "09:30", "10:30");
            throw new AssertionError("겹치는 예약이 거절되지 않았습니다");
        } catch (ApiException e) {
            assertEquals("TIME_CONFLICT", e.getCode().name());
        }
        assertEquals(1, reservationStore.findByRoomId("R5").size());
    }

    /** S11: 사용자별 예약 조회는 방을 가리지 않고 그 사용자의 예약만 모은다. */
    @Test
    void findByUser_collectsAcrossRooms() {
        service.create("R6", "U-A", "09:00", "10:00");
        service.create("R7", "U-A", "14:00", "15:00");
        service.create("R6", "U-B", "10:00", "11:00");

        List<RoomReservation> found = service.findByUser("U-A");

        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(r -> r.getRoomId().equals("R6")));
        assertTrue(found.stream().anyMatch(r -> r.getRoomId().equals("R7")));
    }

    // ---- 테스트 보조 코드 ----

    private enum Outcome { SUCCESS, CONFLICT }

    private record Result(int successCount, int conflictCount) {
    }

    private Outcome createOutcome(String roomId, String userId, String from, String to) {
        try {
            service.create(roomId, userId, from, to);
            return Outcome.SUCCESS;
        } catch (ApiException e) {
            return Outcome.CONFLICT;
        }
    }

    private Result fireConcurrently(int count, IntFunction<Outcome> action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

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
                if (action.apply(idx) == Outcome.SUCCESS) {
                    success.incrementAndGet();
                } else {
                    conflict.incrementAndGet();
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
        return new Result(success.get(), conflict.get());
    }
}
