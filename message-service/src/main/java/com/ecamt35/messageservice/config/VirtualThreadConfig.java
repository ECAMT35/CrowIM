package com.ecamt35.messageservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    @Bean(name = "virtualExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
