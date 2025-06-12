package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.GeocodeService;
import com.dedicatedcode.reitti.repository.GeocodeServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultGeocodeServiceManager implements GeocodeServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGeocodeServiceManager.class);

    private final GeocodeServiceRepository geocodeServiceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int maxErrors;

    public DefaultGeocodeServiceManager(GeocodeServiceRepository geocodeServiceRepository,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceRepository = geocodeServiceRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.maxErrors = maxErrors;
    }

    @Transactional
    @Override
    public Optional<GeocodeResult> reverseGeocode(double latitude, double longitude) {
        List<GeocodeService> availableServices = geocodeServiceRepository.findByEnabledTrueOrderByLastUsedAsc();

        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Optional.empty();
        }

        // Shuffle to distribute load randomly
        Collections.shuffle(availableServices);

        for (GeocodeService service : availableServices) {
            try {
                Optional<GeocodeResult> result = performGeocode(service, latitude, longitude);
                if (result.isPresent()) {
                    recordSuccess(service);
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Geocoding failed for service {}: {}", service.getName(), e.getMessage());
                recordError(service);
            }
        }

        return Optional.empty();
    }

    private Optional<GeocodeResult> performGeocode(GeocodeService service, double latitude, double longitude) {
        String url = service.getUrlTemplate()
                .replace("{lat}", String.valueOf(latitude))
                .replace("{lng}", String.valueOf(longitude));

        logger.debug("Geocoding with service {} using URL: {}", service.getName(), url);

        try {

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");

            if (features.isArray() && !features.isEmpty()) {
                JsonNode properties = features.get(0).path("properties");

                String label;
                String street;
                String city;
                String district;

                //try to find elements from address;
                JsonNode address = properties.path("address");
                if (address.isMissingNode()) {
                    label = properties.path("formatted").asText("");
                    street = properties.path("street").asText("");
                    city = properties.path("city").asText("");
                    district = properties.path("district").asText("");
                } else {
                    label = properties.path("name").asText("");
                    street = address.path("road").asText("");
                    city = address.path("city").asText("");
                    district = address.path("city_district").asText("");
                }


                if (label.isEmpty() && !street.isEmpty()) {
                    label = street;
                }
                if (StringUtils.hasText(label)) {
                    return Optional.of(new GeocodeResult(label, street, city, district));
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to call geocoding service: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    private void recordSuccess(GeocodeService service) {
        service.setLastUsed(Instant.now());
        geocodeServiceRepository.save(service);
    }

    private void recordError(GeocodeService service) {
        service.setErrorCount(service.getErrorCount() + 1);
        service.setLastError(Instant.now());

        if (service.getErrorCount() >= maxErrors) {
            service.setEnabled(false);
            logger.warn("Geocoding service {} disabled due to too many errors ({}/{})",
                    service.getName(), service.getErrorCount(), maxErrors);
        }

        geocodeServiceRepository.save(service);
    }
}
