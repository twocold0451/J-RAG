package com.twocold.jrag;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan("com.twocold.jrag.config")
@EnableAsync
public class JragApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(JragApplication.class, args);
    }

    /**
     * 加载 .env 文件（仅用于本地开发）。
     * 线上环境使用 Docker/K8s 环境变量，不会读取此文件。
     * 优先级：系统环境变量 > 系统属性 > .env 文件
     */
    private static void loadDotenvIfPresent() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return; // 无 .env 文件时静默跳过（线上环境）
        }

        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMalformed()
                    .load();

            int loadedCount = 0;
            for (DotenvEntry entry : dotenv.entries()) {
                // 只在未设置时才加载（不覆盖已有值）
                if (System.getProperty(entry.getKey()) == null
                        && System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                log.info("已从 .env 文件加载 {} 个环境变量（本地开发模式）", loadedCount);
            }
        } catch (Exception e) {
            log.warn("加载 .env 文件失败，将使用系统环境变量: {}", e.getMessage());
        }
    }

}
