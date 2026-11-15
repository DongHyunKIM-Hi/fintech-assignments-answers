package com.practicefintech.refundrelay.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** 2단계에서 추가. 재시도 횟수와 간격, 워커 스레드 풀 크기. */
@Getter
@Setter
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    private int poolSize;
    private long tickIntervalMs;
    private int maxAttempts;
    private List<Long> retryBackoffMs;
}
