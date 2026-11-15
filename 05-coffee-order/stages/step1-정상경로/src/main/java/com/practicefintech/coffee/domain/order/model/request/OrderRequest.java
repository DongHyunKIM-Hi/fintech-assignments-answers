package com.practicefintech.coffee.domain.order.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1단계에서는 검증 애너테이션을 붙이지 않는다. (2단계에서 추가) */
@Getter
@NoArgsConstructor
public class OrderRequest {

    private Long userId;

    private Long menuId;
}
