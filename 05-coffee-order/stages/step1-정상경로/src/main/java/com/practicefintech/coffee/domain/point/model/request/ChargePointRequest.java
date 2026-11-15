package com.practicefintech.coffee.domain.point.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [1단계에서 일부러 하지 않는 것]
 * 검증 애너테이션(@NotNull, @Min, @Max)을 붙이지 않았고, 컨트롤러에도 @Valid가 없다.
 * 그래서 0원이나 음수 금액, 비어 있는 사용자 ID가 그대로 들어온다.
 * 입력 검증과 400 응답은 2단계에서 넣는다.
 */
@Getter
@NoArgsConstructor
public class ChargePointRequest {

    private Long userId;

    private Long amount;
}
