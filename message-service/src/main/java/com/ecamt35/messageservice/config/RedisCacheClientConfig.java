package com.ecamt35.messageservice.config;

import com.ecamt35.messageservice.util.RedisCacheClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisCacheClientConfig {

    @Bean
    public RedisCachePropertiesConfig cachePropertiesConfig() {
        return new RedisCachePropertiesConfig();
    }

    @Bean
    public RedisCacheClient redisCacheClient(RedisTemplate<String, Object> redisTemplateObject,
                                             RedissonClient redissonClient,
                                             ObjectMapper objectMapper,
                                             RedisCachePropertiesConfig props) {
        return new RedisCacheClient(redisTemplateObject, redissonClient, objectMapper, props);
    }
}
