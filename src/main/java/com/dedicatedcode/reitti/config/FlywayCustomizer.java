package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class FlywayCustomizer {
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(@Value("${reitti.geocoding.photon.base-url:}") String photonBaseUrl) {
        return configuration -> configuration.placeholders(Map.of("photon.baseUrl", photonBaseUrl));
    }
}
