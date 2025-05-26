package com.dedicatedcode.reitti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReittiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReittiApplication.class, args);
    }
}
