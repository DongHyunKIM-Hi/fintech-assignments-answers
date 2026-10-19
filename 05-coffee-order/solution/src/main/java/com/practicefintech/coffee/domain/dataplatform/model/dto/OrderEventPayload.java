package com.practicefintech.coffee.domain.dataplatform.model.dto;

import com.practicefintech.coffee.common.entity.OrderEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 데이터 수집 플랫폼에 보내는 내용. 요구사항의 사용자 식별값, 메뉴 ID, 결제 금액에
 * 이벤트 ID를 더했다. 같은 이벤트가 두 번 전송돼도 받는 쪽이 이벤트 ID로 걸러낼 수 있다.
 */
@Getter
@ToString
@AllArgsConstructor
public class OrderEventPayload {

    private Long eventId;
    private Long userId;
    private Long menuId;
    private long amount;

    public static OrderEventPayload from(OrderEvent event) {
        return new OrderEventPayload(event.getId(), event.getUserId(), event.getMenuId(), event.getAmount());
    }
}
