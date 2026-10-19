package com.practicefintech.coffee.domain.dataplatform.client;

import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 데이터 수집 플랫폼 Mock. 실제 플랫폼이 없으므로 받은 내용을 로그로 남기고 메모리에 모은다.
 * 이벤트 ID를 키로 저장해, 같은 이벤트를 두 번 받아도 한 번만 남긴다.
 */
@Slf4j
@Component
public class MockDataPlatformClient implements DataPlatformClient {

    private final Map<Long, OrderEventPayload> received = new ConcurrentHashMap<>();

    @Override
    public void send(OrderEventPayload payload) {
        received.putIfAbsent(payload.getEventId(), payload);
        log.info("[데이터 수집 플랫폼] 주문 수신 {}", payload);
    }

    public List<OrderEventPayload> getReceived() {
        return List.copyOf(received.values());
    }
}
