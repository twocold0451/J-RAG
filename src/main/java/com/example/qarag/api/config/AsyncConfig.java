package com.example.qarag.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "searchExecutor")
    public Executor searchExecutor() {
        // Since we are on Java 21, we can directly use Virtual Threads
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
