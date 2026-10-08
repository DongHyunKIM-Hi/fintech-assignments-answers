package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 가상 시세 서버가 내려 주는 환율 응답 형식. */
@Getter
@NoArgsConstructor
public class ExternalFxResponse {
    private String pair;
    private BigDecimal rate;
    private Instant asOf;
}
