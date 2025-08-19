package com.ecamt35.userservice.config;

import com.ecamt35.userservice.util.RedisStrategyComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisStrategyConfig {

    @Bean
    public RedisStrategyComponent redisStrategyComponent() {
        return new RedisStrategyComponent();
    }

}