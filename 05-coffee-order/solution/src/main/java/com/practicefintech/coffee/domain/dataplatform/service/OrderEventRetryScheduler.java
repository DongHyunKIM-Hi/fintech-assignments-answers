package com.practicefintech.coffee.domain.dataplatform.service;

import com.practicefintech.coffee.common.entity.OrderEvent;
import com.practicefintech.coffee.common.enums.OrderEventStatus;
import com.practicefintech.coffee.domain.order.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전송하지 못한 주문 이벤트(PENDING)를 주기적으로 다시 보낸다.
 *
 * 서버가 여러 대이면 스케줄러도 서버마다 돈다. 같은 이벤트를 여러 서버가 동시에 보내지 않도록
 * Redis 락을 잡은 서버 한 대만 실행한다. 락을 못 잡은 서버는 기다리지 않고 이번 차례를 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventRetryScheduler {

    private final OrderEventRepository orderEventRepository;
    private final OrderEventSender orderEventSender;
    private final RedissonClient redissonClient;
    private final Clock clock;

    /** 방금 만들어진 이벤트는 비동기 전송이 처리 중일 수 있으므로 이 시간이 지난 것만 다시 보낸다. */
    @Value("${coffee.outbox-retry-grace-seconds}")
    private long graceSeconds;

    @Scheduled(fixedDelayString = "${coffee.outbox-retry-millis}")
    public void retryPendingEvents() {
        RLock lock = redissonClient.getLock("lock:order-event-retry");
        if (!lock.tryLock()) {
            return;
        }
        try {
            LocalDateTime before = LocalDateTime.now(clock).minusSeconds(graceSeconds);
            List<OrderEvent> pending = orderEventRepository
                    .findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OrderEventStatus.PENDING, before);
            if (!pending.isEmpty()) {
                log.info("미전송 주문 이벤트 {}건 재전송", pending.size());
            }
            pending.forEach(orderEventSender::send);
        } finally {
            lock.unlock();
        }
    }
}
