package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class TestConfiguration {
    private final AtomicInteger geocodes = new AtomicInteger(1);

    @Bean
    public GeocodeServiceManager geocodeServiceManager() {
        return new GeocodeServiceManager() {
            @Override
            public Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse) {
                String label = significantPlace.getLatitudeCentroid() + "," + significantPlace.getLongitudeCentroid();
                return Optional.of(new GeocodeResult(label, "Test Street " + geocodes.getAndIncrement(), "1", "Test City", "12345","Test District", "de", SignificantPlace.PlaceType.OTHER));
            }

            @Override
            public Map<String, Object> test(GeocodeService service, double testLat, double testLng) {
                return null;
            }

            @Override
            public Map<GeocoderType, List<GeocodeResult>> reverseGeocodeAll(SignificantPlace significantPlace) {
                return Map.of();
            }
        };
    }
}
