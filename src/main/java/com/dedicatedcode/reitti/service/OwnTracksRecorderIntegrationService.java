package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OwnTracksRecorderIntegrationJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class OwnTracksRecorderIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(OwnTracksRecorderIntegrationService.class);
    
    private final OwnTracksRecorderIntegrationJdbcService jdbcService;
    private final RestTemplate restTemplate;

    public OwnTracksRecorderIntegrationService(OwnTracksRecorderIntegrationJdbcService jdbcService) {
        this.jdbcService = jdbcService;
        this.restTemplate = new RestTemplate();
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
}
