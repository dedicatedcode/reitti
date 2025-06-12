package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class TestConfiguration {

    @Bean
    public GeocodeServiceManager geocodeServiceManager() {
        return (latitude, longitude) -> {
            String label = latitude + "," + longitude;
            return Optional.of(new GeocodeResult(label, "Test Street 1", "Test City", "Test District"));
        };
    }
}
