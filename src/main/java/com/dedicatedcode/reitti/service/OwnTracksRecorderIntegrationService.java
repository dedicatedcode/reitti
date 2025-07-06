package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OwnTracksRecorderIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class OwnTracksRecorderIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(OwnTracksRecorderIntegrationService.class);
    
    private final OwnTracksRecorderIntegrationJdbcService jdbcService;
    private final UserJdbcService userJdbcService;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    public OwnTracksRecorderIntegrationService(OwnTracksRecorderIntegrationJdbcService jdbcService,
                                             UserJdbcService userJdbcService,
                                             RabbitTemplate rabbitTemplate) {
        this.jdbcService = jdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = new RestTemplate();
    }

    @Scheduled(cron = "${reitti.imports.owntracks-recorder.schedule}")
    @Transactional
    void importNewData() {
        logger.debug("Starting OwnTracks Recorder data import");
        
        List<User> allUsers = userJdbcService.findAll();
        int processedIntegrations = 0;
        int totalLocationPoints = 0;
        
        for (User user : allUsers) {
            Optional<OwnTracksRecorderIntegration> integrationOpt = jdbcService.findByUser(user);
            
            if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
                continue;
            }
            
            OwnTracksRecorderIntegration integration = integrationOpt.get();
            processedIntegrations++;
            
            try {
                Instant fromTime;
                if (integration.getLastSuccessfulFetch() != null) {
                    fromTime = integration.getLastSuccessfulFetch().minus(1, ChronoUnit.MINUTES);
                } else {
                    fromTime = null;
                }
                
                // Fetch location data from OwnTracks Recorder
                List<OwntracksLocationRequest> locationData = fetchLocationData(integration, fromTime);
                
                if (!locationData.isEmpty()) {
                    // Convert to LocationPoints and filter valid ones
                    List<LocationDataRequest.LocationPoint> validPoints = new ArrayList<>();
                    
                    for (OwntracksLocationRequest owntracksData : locationData) {
                        if (owntracksData.isLocationUpdate()) {
                            LocationDataRequest.LocationPoint locationPoint = owntracksData.toLocationPoint();
                            if (locationPoint.getTimestamp() != null) {
                                validPoints.add(locationPoint);
                            }
                        }
                    }
                    
                    if (!validPoints.isEmpty()) {
                        // Send to queue like IngestApiController does
                        LocationDataEvent event = new LocationDataEvent(user.getUsername(), validPoints);
                        
                        rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE_NAME,
                            RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                            event
                        );
                        
                        totalLocationPoints += validPoints.size();
                        logger.debug("Imported {} location points for user {}", validPoints.size(), user.getUsername());
                    }
                }
                
                // Update lastSuccessfulFetch timestamp
                OwnTracksRecorderIntegration updatedIntegration = integration.withLastSuccessfulFetch(Instant.now());
                jdbcService.update(updatedIntegration);
                
            } catch (Exception e) {
                logger.error("Failed to import data for user {} from OwnTracks Recorder: {}", 
                           user.getUsername(), e.getMessage(), e);
            }
        }
        
        logger.info("OwnTracks Recorder import completed: processed {} integrations, imported {} location points", 
                   processedIntegrations, totalLocationPoints);
    }

    public Optional<OwnTracksRecorderIntegration> getIntegrationForUser(User user) {
        return jdbcService.findByUser(user);
    }

    public OwnTracksRecorderIntegration saveIntegration(User user, String baseUrl, String username, String deviceId, boolean enabled) {
        // Validate inputs
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be empty");
        }

        // Normalize base URL (remove trailing slash)
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        Optional<OwnTracksRecorderIntegration> existingIntegration = jdbcService.findByUser(user);
        
        if (existingIntegration.isPresent()) {
            OwnTracksRecorderIntegration existing = existingIntegration.get();
            OwnTracksRecorderIntegration updated = new OwnTracksRecorderIntegration(
                    existing.getId(),
                    normalizedBaseUrl,
                    username.trim(),
                    deviceId.trim(),
                    enabled,
                    existing.getLastSuccessfulFetch(),
                    existing.getVersion()
            );
            return jdbcService.update(updated);
        } else {
            OwnTracksRecorderIntegration newIntegration = new OwnTracksRecorderIntegration(
                    normalizedBaseUrl,
                    username.trim(),
                    deviceId.trim(),
                    enabled
            );
            return jdbcService.save(user, newIntegration);
        }
    }

    public boolean testConnection(String baseUrl, String username, String deviceId) {
        try {
            String normalizedBaseUrl = baseUrl.trim();
            if (normalizedBaseUrl.endsWith("/")) {
                normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
            }

            String testUrl = normalizedBaseUrl + "/api/0/locations";
            
            logger.debug("Testing OwnTracks Recorder connection to: {}", testUrl);
            
            ResponseEntity<String> response = restTemplate.getForEntity(testUrl, String.class);

            HttpStatus statusCode = (HttpStatus) response.getStatusCode();
            boolean isSuccessful = statusCode.is2xxSuccessful() || 
                                 statusCode == HttpStatus.UNAUTHORIZED || 
                                 statusCode == HttpStatus.FORBIDDEN;
            
            logger.debug("OwnTracks Recorder connection test result: {} (status: {})", isSuccessful, statusCode);
            return isSuccessful;
        } catch (Exception e) {
            logger.warn("Failed to test OwnTracks Recorder connection to {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    public void deleteIntegration(User user) {
        Optional<OwnTracksRecorderIntegration> integration = jdbcService.findByUser(user);
        integration.ifPresent(jdbcService::delete);
    }

    private List<OwntracksLocationRequest> fetchLocationData(OwnTracksRecorderIntegration integration, Instant fromTime) {
        try {
            String apiUrl;
            if (fromTime != null) {
                apiUrl = String.format("%s/api/0/locations?user=%s&device=%s&from=%d",
                        integration.getBaseUrl(),
                        integration.getUsername(),
                        integration.getDeviceId(),
                        fromTime.getEpochSecond());
            } else {
                apiUrl = String.format("%s/api/0/locations?user=%s&device=%s",
                        integration.getBaseUrl(),
                        integration.getUsername(),
                        integration.getDeviceId());
            }
            
            logger.debug("Fetching location data from: {}", apiUrl);
            
            ResponseEntity<List<OwntracksLocationRequest>> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<OwntracksLocationRequest>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully fetched {} location records from OwnTracks Recorder", 
                           response.getBody().size());
                return response.getBody();
            } else {
                logger.warn("Unexpected response from OwnTracks Recorder: {}", response.getStatusCode());
                return Collections.emptyList();
            }
            
        } catch (Exception e) {
            logger.error("Failed to fetch location data from OwnTracks Recorder: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
