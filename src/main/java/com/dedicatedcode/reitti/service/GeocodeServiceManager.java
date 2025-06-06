package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.GeocodeService;
import com.dedicatedcode.reitti.repository.GeocodeServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeocodeServiceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(GeocodeServiceManager.class);
    
    private final GeocodeServiceRepository geocodeServiceRepository;
    private final RestTemplate restTemplate;
    
    public GeocodeServiceManager(GeocodeServiceRepository geocodeServiceRepository, RestTemplate restTemplate) {
        this.geocodeServiceRepository = geocodeServiceRepository;
        this.restTemplate = restTemplate;
    }
    
    @Transactional
    public Optional<String> reverseGeocode(double latitude, double longitude) {
        List<GeocodeService> availableServices = geocodeServiceRepository.findByEnabledTrueOrderByLastUsedAsc();
        
        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Optional.empty();
        }
        
        // Shuffle to distribute load randomly
        Collections.shuffle(availableServices);
        
        for (GeocodeService service : availableServices) {
            try {
                String address = performGeocode(service, latitude, longitude);
                if (address != null && !address.trim().isEmpty()) {
                    recordSuccess(service);
                    return Optional.of(address);
                }
            } catch (Exception e) {
                logger.warn("Geocoding failed for service {}: {}", service.getName(), e.getMessage());
                recordError(service);
            }
        }
        
        return Optional.empty();
    }
    
    private String performGeocode(GeocodeService service, double latitude, double longitude) {
        String url = service.getUrlTemplate()
            .replace("{lat}", String.valueOf(latitude))
            .replace("{lng}", String.valueOf(longitude));
        
        logger.debug("Geocoding with service {} using URL: {}", service.getName(), url);
        
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return extractAddressFromResponse(response, service.getName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call geocoding service: " + e.getMessage(), e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private String extractAddressFromResponse(Map<String, Object> response, String serviceName) {
        if (response == null) {
            return null;
        }
        
        // Handle Nominatim format
        if (response.containsKey("display_name")) {
            return (String) response.get("display_name");
        }
        
        // Handle LocationIQ format (similar to Nominatim)
        if (response.containsKey("display_name")) {
            return (String) response.get("display_name");
        }
        
        // Handle other formats - try to build address from components
        if (response.containsKey("address")) {
            Map<String, Object> address = (Map<String, Object>) response.get("address");
            StringBuilder addressBuilder = new StringBuilder();
            
            // Common address components in order of preference
            String[] components = {"house_number", "road", "neighbourhood", "suburb", 
                                 "city", "town", "village", "state", "country"};
            
            for (String component : components) {
                if (address.containsKey(component)) {
                    if (addressBuilder.length() > 0) {
                        addressBuilder.append(", ");
                    }
                    addressBuilder.append(address.get(component));
                }
            }
            
            return addressBuilder.toString();
        }
        
        logger.warn("Unknown response format from geocoding service {}: {}", serviceName, response);
        return null;
    }
    
    @Transactional
    private void recordSuccess(GeocodeService service) {
        service.setLastUsed(Instant.now());
        geocodeServiceRepository.save(service);
    }
    
    @Transactional
    private void recordError(GeocodeService service) {
        service.setErrorCount(service.getErrorCount() + 1);
        service.setLastError(Instant.now());
        
        if (service.getErrorCount() >= service.getMaxErrors()) {
            service.setEnabled(false);
            logger.warn("Geocoding service {} disabled due to too many errors ({}/{})", 
                       service.getName(), service.getErrorCount(), service.getMaxErrors());
        }
        
        geocodeServiceRepository.save(service);
    }
}
