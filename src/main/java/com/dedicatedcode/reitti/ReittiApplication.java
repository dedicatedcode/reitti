package com.dedicatedcode.reitti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = { OAuth2ClientAutoConfiguration.class })
@EnableAsync
public class ReittiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReittiApplication.class, args);
    }
}
