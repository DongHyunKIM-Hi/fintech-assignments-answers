package com.practicefintech.coffee.domain.dataplatform.service;

import com.practicefintech.coffee.common.entity.OrderEvent;
import com.practicefintech.coffee.common.enums.OrderEventStatus;
import com.practicefintech.coffee.domain.dataplatform.client.DataPlatformClient;
import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;
import com.practicefintech.coffee.domain.order.model.dto.OrderCompletedEvent;
import com.practicefintech.coffee.domain.order.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 주문 이벤트를 데이터 수집 플랫폼으로 보낸다.
 *
 * 주문이 커밋되면 바로 별도 스레드에서 보낸다(실시간 전송). 주문 응답은 전송을 기다리지 않는다.
 * 전송에 실패하면 이벤트가 PENDING으로 남고, {@link OrderEventRetryScheduler}가 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventSender {

    private final OrderEventRepository orderEventRepository;
    private final DataPlatformClient dataPlatformClient;
    private final Clock clock;

    @Async
    @TransactionalEventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        orderEventRepository.findById(event.getOrderEventId()).ifPresent(this::send);
    }

    public void send(OrderEvent event) {
        if (event.getStatus() == OrderEventStatus.SENT) {
            return;
        }
        try {
            dataPlatformClient.send(OrderEventPayload.from(event));
            orderEventRepository.markSent(event.getId(), LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            orderEventRepository.increaseAttempt(event.getId());
            log.warn("데이터 수집 플랫폼 전송 실패. 다음 재시도 때 다시 보냅니다. eventId={}", event.getId(), e);
        }
    }
}
