package com.example.qarag.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "searchExecutor")
    public Executor searchExecutor() {
        // 既然使用的是 Java 21，我们可以直接使用虚拟线程
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
