package com.practicefintech.refundrelay.domain.refund.service;

import com.practicefintech.refundrelay.common.config.WorkerProperties;
import com.practicefintech.refundrelay.common.entity.Refund;
import com.practicefintech.refundrelay.common.enums.FailureReason;
import com.practicefintech.refundrelay.common.enums.PgCallResult;
import com.practicefintech.refundrelay.common.enums.RefundStatus;
import com.practicefintech.refundrelay.common.utils.PgClient;
import com.practicefintech.refundrelay.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
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
 * 접수된 환불을 뒤에서 가상 결제대행사로 전달하는 워커.
 *
 * 2단계에서 추가한 것
 * - 재시도: 실패하면 0.5초, 1초, 2초 뒤에 다시 보낸다. 최초 시도를 포함해 최대 4번. (S9)
 * - 시간 초과: 5초 안에 응답이 없으면 PG_TIMEOUT으로 보고, 같은 refundId로 다시 보낸다. (S10, S12)
 * - 스레드 풀: 건마다 작업 하나를 맡겨, 느린 건 하나가 나머지 전부를 막지 않게 했다.
 *   refundsInFlight는 "지금 보내는 중인 건"을 기억해, 같은 건이 두 번 나가지 않게 한다.
 *
 * 2단계에서 여전히 하지 않는 것 (= 3단계 몫)
 * - 같은 주문의 순서 보장. 막는 단위가 "건"(id)이라, 같은 주문의 환불 두 건이 동시에
 *   결제대행사로 나간다. 앞 건이 아직 끝나지 않았는데 뒤 건이 먼저 끝날 수 있다. (S11 위반)
 *
 * 왜 다음 단계가 필요한가
 * - 3단계에서는 막는 단위를 "건"에서 "주문(orderId)"으로 바꾼다. 주문 하나를 작업 하나로 맡기고,
 *   그 안에서 항상 가장 오래된 미종결 건만 처리한다. 그러면 같은 주문은 한 건씩,
 *   다른 주문은 그대로 동시에 처리된다.
 */
@Component
@RequiredArgsConstructor
public class RefundWorker {

    private final RefundRepository repository;
    private final PgClient pgClient;
    private final WorkerProperties properties;
    /** 지금 결제대행사로 보내는 중인 건(Refund.id). 같은 건이 중복으로 나가는 것만 막는다. */
    private final Set<Long> refundsInFlight = ConcurrentHashMap.newKeySet();
    private ExecutorService pool;

    @jakarta.annotation.PostConstruct
    void init() {
        this.pool = Executors.newFixedThreadPool(properties.getPoolSize());
    }

    @Scheduled(fixedDelayString = "${worker.tick-interval-ms}")
    public void tick() {
        for (Refund due : repository.findDueRefunds(Instant.now())) {
            Long id = due.getId();
            // 같은 주문인지는 보지 않는다. 그래서 같은 주문의 두 건이 나란히 나간다. (3단계에서 수정)
            if (refundsInFlight.add(id)) {
                pool.submit(() -> {
                    try {
                        processOneAttempt(id);
                    } finally {
                        refundsInFlight.remove(id);
                    }
                });
            }
        }
    }

    /** 환불 한 건에 대한 한 번의 처리 시도. */
    void processOneAttempt(Long id) {
        Optional<Refund> found = repository.findById(id);
        if (found.isEmpty() || found.get().getStatus() != RefundStatus.ACCEPTED) {
            return;
        }
        Refund refund = found.get();
        refund.markSending();
        repository.save(refund);

        PgCallResult result = pgClient.send(refund.getRefundId(), refund.getOrderId(), refund.getRefundAmount());

        if (result == PgCallResult.SUCCESS) {
            refund.markCompleted(Instant.now());
        } else {
            FailureReason reason = toFailureReason(result);
            if (refund.getAttemptCount() >= properties.getMaxAttempts()) {
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
        List<Long> backoffs = properties.getRetryBackoffMs();
        int index = Math.max(0, Math.min(attemptCount - 1, backoffs.size() - 1));
        return backoffs.get(index);
    }
}
