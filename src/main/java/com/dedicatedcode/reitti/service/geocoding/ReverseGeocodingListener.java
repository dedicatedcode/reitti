package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.repository.SignificantPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.dedicatedcode.reitti.config.RabbitMQConfig;

import java.util.Optional;

@Component
public class ReverseGeocodingListener {
    private static final Logger logger = LoggerFactory.getLogger(ReverseGeocodingListener.class);
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse?format=geocodejson&lat=%s&lon=%s";
    
    private final SignificantPlaceRepository significantPlaceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ReverseGeocodingListener(
            SignificantPlaceRepository significantPlaceRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.significantPlaceRepository = significantPlaceRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @RabbitListener(queues = RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, concurrency = "1-16")
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Received SignificantPlaceCreatedEvent for place ID: {}", event.getPlaceId());
        
        Optional<SignificantPlace> placeOptional = significantPlaceRepository.findById(event.getPlaceId());
        if (placeOptional.isEmpty()) {
            logger.error("Could not find SignificantPlace with ID: {}", event.getPlaceId());
            return;
        }
        
        SignificantPlace place = placeOptional.get();
        
        try {
            String url = String.format(NOMINATIM_URL, place.getLatitudeCentroid(), place.getLongitudeCentroid());
            String response = restTemplate.getForObject(url, String.class);
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");
            
            if (features.isArray() && !features.isEmpty()) {
                JsonNode geocoding = features.get(0).path("properties").path("geocoding");
                
                String label = geocoding.path("label").asText("");
                String street = geocoding.path("street").asText("");
                String city = geocoding.path("city").asText("");
                String district = geocoding.path("district").asText("");
                
                // Set the name to the street or district if available
                if (!street.isEmpty()) {
                    place.setName(street);
                } else if (!district.isEmpty()) {
                    place.setName(district);
                } else if (!city.isEmpty()) {
                    place.setName(city);
                }
                
                // Set the address to the full label
                place.setAddress(label);
                
                // Save the updated place
                significantPlaceRepository.saveAndFlush(place);
                logger.info("Updated place ID: {} with geocoding data: {}", place.getId(), label);
            } else {
                logger.warn("No geocoding results found for place ID: {}", place.getId());
            }
        } catch (Exception e) {
            logger.error("Error during reverse geocoding for place ID: {}", place.getId(), e);
        }
    }
}
