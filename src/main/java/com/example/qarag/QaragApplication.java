package com.example.qarag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.qarag.config")
@EnableAsync // Add this annotation
public class QaragApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaragApplication.class, args);
    }

}
