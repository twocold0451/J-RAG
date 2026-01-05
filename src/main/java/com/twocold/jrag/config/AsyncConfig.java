package com.twocold.jrag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 专门用于文档摄取（Ingestion）的线程池。
     * 限制核心线程数为 2，以控制对 Embedding/Vision API 的并发请求量，防止触发限流 (429 Too Many Requests)。
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor ingestionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // 限制同时处理文档的线程数
        executor.setMaxPoolSize(2);  // 不允许突发超过此数量
        executor.setQueueCapacity(500); // 允许排队等待的任务数
        executor.setThreadNamePrefix("Ingestion-");
        executor.initialize();
        return executor;
    }

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
