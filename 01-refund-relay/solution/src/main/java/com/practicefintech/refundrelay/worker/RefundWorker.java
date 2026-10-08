package com.practicefintech.refundrelay.worker;

import com.practicefintech.refundrelay.refund.FailureReason;
import com.practicefintech.refundrelay.refund.Refund;
import com.practicefintech.refundrelay.refund.RefundRepository;
import com.practicefintech.refundrelay.refund.RefundStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 접수된 환불을 뒤에서 가상 결제대행사로 전달하는 워커. 두 가지를 동시에 만족해야 한다.
 *   1) 같은 주문(orderId)의 환불은 하나씩만, 접수 순서대로 처리한다 (B7).
 *   2) 서로 다른 주문끼리는 병렬로 처리해, 한 주문이 오래 걸려도 다른 주문이 막히지 않는다.
 * 그래서 "주문 하나 = 스레드 풀의 작업 하나"로 두고, ordersInFlight로 같은 주문이
 * 동시에 두 번 처리되지 않게만 막는다. 같은 주문의 다음 건은 이번 시도가 끝난 뒤
 * 다음 tick에서 자연스럽게 골라진다(오래된 순 조회이므로 순서가 보장된다).
 */
@Component
public class RefundWorker {

    private final RefundRepository repository;
    private final PgClient pgClient;
    private final WorkerProperties properties;
    private final ExecutorService pool;
    private final Set<String> ordersInFlight = ConcurrentHashMap.newKeySet();

    public RefundWorker(RefundRepository repository, PgClient pgClient, WorkerProperties properties) {
        this.repository = repository;
        this.pgClient = pgClient;
        this.properties = properties;
        this.pool = Executors.newFixedThreadPool(properties.poolSize());
    }

    @Scheduled(fixedDelayString = "${worker.tick-interval-ms}")
    public void tick() {
        for (String orderId : repository.findOrderIdsWithDueRefunds(Instant.now())) {
            if (ordersInFlight.add(orderId)) {
                pool.submit(() -> {
                    try {
                        processOneAttempt(orderId);
                    } finally {
                        ordersInFlight.remove(orderId);
                    }
                });
            }
        }
    }

    private void processOneAttempt(String orderId) {
        Optional<Refund> due = repository.findFirstByOrderIdAndStatusAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(
                orderId, RefundStatus.ACCEPTED, Instant.now());
        if (due.isEmpty()) {
            return;
        }
        Refund refund = due.get();
        refund.markSending();
        repository.save(refund);

        PgCallResult result = pgClient.send(refund.getRefundId(), refund.getOrderId(), refund.getRefundAmount());

        if (result == PgCallResult.SUCCESS) {
            refund.markCompleted(Instant.now());
        } else {
            FailureReason reason = toFailureReason(result);
            if (refund.getAttemptCount() >= properties.maxAttempts()) {
                refund.markFinalFailure(reason, Instant.now());
            } else {
                refund.scheduleRetry(reason, Instant.now().plusMillis(backoffFor(refund.getAttemptCount())));
            }
        }
        repository.save(refund);
    }

    private FailureReason toFailureReason(PgCallResult result) {
        return switch (result) {
            case DECLINED -> FailureReason.PG_DECLINED;
            case TIMEOUT -> FailureReason.PG_TIMEOUT;
            default -> FailureReason.PG_ERROR;
        };
    }

    private long backoffFor(int attemptCount) {
        List<Long> backoffs = properties.retryBackoffMs();
        int index = Math.max(0, Math.min(attemptCount - 1, backoffs.size() - 1));
        return backoffs.get(index);
    }
}
