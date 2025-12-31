package com.twocold.jrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan("com.twocold.jrag.config")
@EnableAsync // Add this annotation
public class QaragApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaragApplication.class, args);
    }

}
