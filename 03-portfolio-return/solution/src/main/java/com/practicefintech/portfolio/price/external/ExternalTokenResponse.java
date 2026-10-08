package com.practicefintech.portfolio.price.external;

public record ExternalTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
