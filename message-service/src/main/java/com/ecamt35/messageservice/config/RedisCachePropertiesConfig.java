package com.ecamt35.messageservice.config;

import lombok.Data;

import java.time.Duration;

@Data
public class RedisCachePropertiesConfig {
    /**
     * 空值占位 TTL（防穿透）
     */
    private Duration nullTtl = Duration.ofMinutes(2);

    /**
     * 锁等待时间（tryLock 等多久）
     */
    private Duration lockWaitTime = Duration.ofSeconds(2);

    /**
     * 过期时间抖动下界（秒）
     */
    private long jitterSecondsMin = 60;

    /**
     * 过期时间抖动上界（秒）
     */
    private long jitterSecondsMax = 300;

    /**
     * 锁 key 前缀
     */
    private String lockPrefix = "lock:cache:";

}