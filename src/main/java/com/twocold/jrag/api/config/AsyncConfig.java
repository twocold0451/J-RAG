package com.twocold.jrag.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "searchExecutor")
    public Executor searchExecutor() {
        // 既然使用的是 Java 21，我们可以直接使用虚拟线程
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * Spring @Async 默认使用的执行器。
     * 使用虚拟线程以支持高并发的 I/O 密集型任务（如 LangFuse 上报）。
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
