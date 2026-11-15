package com.practicefintech.portfolio.domain.portfolio.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1단계에는 검증 애너테이션이 없다. 이름 길이(1~50자), 보유 상품 개수(1~20개),
 * 같은 code 중복 금지는 2단계에서 검사한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequest {
    private String name;
    private List<HoldingRequest> holdings;
}
