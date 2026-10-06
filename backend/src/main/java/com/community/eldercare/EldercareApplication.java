package com.community.eldercare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EldercareApplication {
    public static void main(String[] args) {
        SpringApplication.run(EldercareApplication.class, args);
    }
}
