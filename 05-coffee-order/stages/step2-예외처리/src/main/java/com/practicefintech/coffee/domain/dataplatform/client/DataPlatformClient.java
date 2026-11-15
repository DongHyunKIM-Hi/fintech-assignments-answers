package com.practicefintech.coffee.domain.dataplatform.client;

import com.practicefintech.coffee.domain.dataplatform.model.dto.OrderEventPayload;

/** 데이터 수집 플랫폼. 실패하면 예외를 던진다. */
public interface DataPlatformClient {

    void send(OrderEventPayload payload);
}
