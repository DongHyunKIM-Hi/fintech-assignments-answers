package com.practicefintech.portfolio.domain.portfolio.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1단계에는 검증 애너테이션이 없다. 수량 0이나 음수, 존재하지 않는 코드가 그대로 들어온다.
 * {@code @NotBlank}, {@code @Min(1)}, {@code @Max(1_000_000)}는 2단계에서 붙인다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRequest {
    private String code;
    private Long quantity;
}
