package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.config.PgClientProperties;
import com.practicefintech.refundrelay.common.config.WorkerProperties;
import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.enums.FailureReason;
import com.practicefintech.refundrelay.common.enums.PgCallResult;
import com.practicefintech.refundrelay.common.enums.RefundStatus;
import com.practicefintech.refundrelay.common.utils.PgClient;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 처리 워커의 핵심 규칙 검증. 스레드나 대기 없이 처리 시도를 한 번씩 직접 호출해
 * 결정적으로 확인한다. (시나리오 S8, S9, S11, S12의 핵심 규칙)
 */
@DataJpaTest
class RefundWorkerTest {

    @Autowired
    RefundRepository repository;

    StubPgClient pgClient;
    RefundWorker worker;

    @BeforeEach
    void setUp() {
        pgClient = new StubPgClient();
        WorkerProperties properties = new WorkerProperties();
        properties.setPoolSize(1);
        properties.setTickIntervalMs(300);
        properties.setMaxAttempts(4);
        properties.setRetryBackoffMs(List.of(0L, 0L, 0L)); // 테스트에서는 기다리지 않고 바로 재시도
        worker = new RefundWorker(repository, pgClient, properties);
    }

    @Test
    @DisplayName("S11: 같은 주문에서 앞 건이 재시도 대기 중이면 뒤 건을 결제대행사에 보내지 않는다")
    void doesNotSendNextRefundWhileOldestIsWaitingForRetry() {
        Instant now = Instant.now();
        Refund first = Refund.accepted("R-1", "ORD-1", 10_000, 1_000, null, now.minusSeconds(10));
        first.markSending();
        first.scheduleRetry(FailureReason.PG_DECLINED, now.plusSeconds(3600)); // 재시도 대기 중
        repository.save(first);
        repository.save(Refund.accepted("R-2", "ORD-1", 10_000, 1_000, null, now)); // 바로 처리 가능

        worker.processOneAttempt("ORD-1");

        assertTrue(pgClient.sentRefundIds.isEmpty(), "앞 건이 종결되기 전에는 아무것도 보내면 안 된다");
        assertEquals(RefundStatus.ACCEPTED, repository.findByRefundId("R-2").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("S8, S11: 접수 순서가 빠른 건부터 보내고, 성공하면 COMPLETED와 완료 시각을 기록한다")
    void sendsOldestFirstAndMarksCompleted() {
        Instant now = Instant.now();
        repository.save(Refund.accepted("R-1", "ORD-1", 10_000, 1_000, null, now.minusSeconds(10)));
        repository.save(Refund.accepted("R-2", "ORD-1", 10_000, 1_000, null, now));
        pgClient.enqueue(PgCallResult.SUCCESS, PgCallResult.SUCCESS);

        worker.processOneAttempt("ORD-1");
        assertEquals(List.of("R-1"), pgClient.sentRefundIds);
        Refund first = repository.findByRefundId("R-1").orElseThrow();
        assertEquals(RefundStatus.COMPLETED, first.getStatus());
        assertNotNull(first.getCompletedAt());

        worker.processOneAttempt("ORD-1");
        assertEquals(List.of("R-1", "R-2"), pgClient.sentRefundIds);
        assertEquals(RefundStatus.COMPLETED, repository.findByRefundId("R-2").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("S9: 계속 실패하면 최대 4번 시도한 뒤 FAILED와 실패 사유를 기록한다")
    void marksFinalFailureAfterMaxAttempts() {
        repository.save(Refund.accepted("R-1", "ORD-1", 10_000, 1_000, null, Instant.now()));
        pgClient.enqueue(PgCallResult.DECLINED, PgCallResult.DECLINED, PgCallResult.DECLINED, PgCallResult.DECLINED);

        for (int i = 0; i < 4; i++) {
            worker.processOneAttempt("ORD-1");
        }

        Refund refund = repository.findByRefundId("R-1").orElseThrow();
        assertEquals(4, pgClient.sentRefundIds.size(), "최초 시도를 포함해 정확히 4번 시도해야 한다");
        assertEquals(4, refund.getAttemptCount());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals(FailureReason.PG_DECLINED, refund.getFailureReason());

        worker.processOneAttempt("ORD-1");
        assertEquals(4, pgClient.sentRefundIds.size(), "종결된 건은 다시 보내지 않는다");
    }

    @Test
    @DisplayName("S12: 시간 초과 후 재시도할 때도 같은 refundId를 사용한다")
    void retriesWithSameRefundIdAfterTimeout() {
        repository.save(Refund.accepted("R-1", "ORD-1", 10_000, 1_000, null, Instant.now()));
        pgClient.enqueue(PgCallResult.TIMEOUT, PgCallResult.SUCCESS);

        worker.processOneAttempt("ORD-1");
        worker.processOneAttempt("ORD-1");

        assertEquals(List.of("R-1", "R-1"), pgClient.sentRefundIds, "재시도에 새 ID를 만들면 이중 환불이 된다");
        assertEquals(RefundStatus.COMPLETED, repository.findByRefundId("R-1").orElseThrow().getStatus());
    }

    /** 실제 HTTP를 보내지 않고 정해 둔 결과를 돌려주는 가짜 결제대행사 클라이언트. */
    static class StubPgClient extends PgClient {

        final List<String> sentRefundIds = new ArrayList<>();
        private final Deque<PgCallResult> script = new ArrayDeque<>();

        StubPgClient() {
            super(testProperties(), new JsonMapper());
        }

        private static PgClientProperties testProperties() {
            PgClientProperties properties = new PgClientProperties();
            properties.setBaseUrl("http://localhost:0");
            properties.setCallTimeoutMs(1000);
            return properties;
        }

        void enqueue(PgCallResult... results) {
            script.addAll(List.of(results));
        }

        @Override
        public PgCallResult send(String refundId, String orderId, long amount) {
            sentRefundIds.add(refundId);
            return script.isEmpty() ? PgCallResult.SUCCESS : script.poll();
        }
    }
}
