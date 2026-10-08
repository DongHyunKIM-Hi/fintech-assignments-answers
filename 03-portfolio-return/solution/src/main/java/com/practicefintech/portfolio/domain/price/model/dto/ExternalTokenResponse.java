package com.practicefintech.portfolio.domain.price.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExternalTokenResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
}
