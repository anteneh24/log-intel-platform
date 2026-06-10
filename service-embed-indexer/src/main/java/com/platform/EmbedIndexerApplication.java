package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.platform")
public class EmbedIndexerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbedIndexerApplication.class, args);
    }
}
