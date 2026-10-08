package com.practicefintech.refundrelay.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(int poolSize, long tickIntervalMs, int maxAttempts, List<Long> retryBackoffMs) {
}
