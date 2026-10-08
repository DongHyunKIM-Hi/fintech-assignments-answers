package com.practicefintech.refundrelay.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    private int poolSize;
    private long tickIntervalMs;
    private int maxAttempts;
    private List<Long> retryBackoffMs;
}
