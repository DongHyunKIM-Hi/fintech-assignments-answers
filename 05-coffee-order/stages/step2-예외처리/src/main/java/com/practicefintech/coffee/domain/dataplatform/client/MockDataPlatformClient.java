package com.practicefintech.coffee.domain.dataplatform.client;

import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 데이터 수집 플랫폼 Mock. 실제 플랫폼이 없으므로 받은 내용을 로그로 남기고 메모리에 모은다.
 *
 * 3단계에서는 같은 주문이 두 번 전송될 수 있어, 이벤트 ID를 키로 하는 Map으로 바뀐다.
 */
@Slf4j
@Component
public class MockDataPlatformClient implements DataPlatformClient {

    private final List<OrderEventPayload> received = new CopyOnWriteArrayList<>();

    @Override
    public void send(OrderEventPayload payload) {
        received.add(payload);
        log.info("[데이터 수집 플랫폼] 주문 수신 {}", payload);
    }

    public List<OrderEventPayload> getReceived() {
        return List.copyOf(received);
    }
}
