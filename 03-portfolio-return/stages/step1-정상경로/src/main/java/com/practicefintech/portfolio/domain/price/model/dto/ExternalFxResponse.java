package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 가상 시세 서버가 내려 주는 환율 응답 형식.
 * 1단계는 환율을 쓰지 않아서 이 DTO를 아직 아무도 부르지 않는다. 2단계에서 쓴다.
 */
@Getter
@NoArgsConstructor
public class ExternalFxResponse {
    private String pair;
    private BigDecimal rate;
    private Instant asOf;
}
