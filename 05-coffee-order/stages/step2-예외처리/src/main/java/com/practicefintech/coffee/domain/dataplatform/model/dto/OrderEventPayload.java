package com.practicefintech.coffee.domain.dataplatform.model.dto;

import com.practicefintech.coffee.common.entity.CoffeeOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 데이터 수집 플랫폼에 보내는 내용. 요구사항이 정한 사용자 식별값, 메뉴 ID, 결제 금액을 담는다.
 *
 * [2단계에서 아직 하지 않는 것]
 * 3단계에서는 전송 실패분을 다시 보내게 되고, 그때 같은 주문이 두 번 전송될 수 있다.
 * 그래서 3단계에서 이벤트 ID를 더해 받는 쪽이 중복을 걸러낼 수 있게 한다.
 */
@Getter
@ToString
@AllArgsConstructor
public class OrderEventPayload {

    private Long userId;
    private Long menuId;
    private long amount;

    public static OrderEventPayload from(CoffeeOrder order) {
        return new OrderEventPayload(order.getUserId(), order.getMenuId(), order.getPrice());
    }
}
