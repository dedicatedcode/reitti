package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.SignificantPlace;
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
        return significantPlace -> {
            String label = significantPlace.getLatitudeCentroid() + "," + significantPlace.getLongitudeCentroid();
            return Optional.of(new GeocodeResult(label, "Test Street " + geocodes.getAndIncrement(), "1", "Test City", "12345","Test District", "de", SignificantPlace.PlaceType.OTHER));
        };
    }
}
