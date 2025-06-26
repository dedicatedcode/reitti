package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class TestConfiguration {
    private final AtomicInteger geocodes = new AtomicInteger(1);

    @Bean
    public GeocodeServiceManager geocodeServiceManager() {
        return (latitude, longitude) -> {
            String label = latitude + "," + longitude;
            return Optional.of(new GeocodeResult(label, "Test Street " + geocodes.getAndIncrement(), "Test City", "Test District"));
        };
    }
}
