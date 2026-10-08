package com.practicefintech.portfolio.price;

import java.time.Instant;

/** 발급받은 토큰과 만료 시각. 매번 새로 받지 않고 만료 직전까지 재사용한다 (S10). */
final class TokenCache {
    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    boolean isValid(long marginSeconds) {
        return token != null && Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
    }

    void set(String token, long expiresInSeconds) {
        this.token = token;
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds);
    }

    String get() {
        return token;
    }

    void invalidate() {
        this.expiresAt = Instant.EPOCH;
    }
}
