package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalTokenRequest {
    private String clientId;
    private String clientSecret;
}
