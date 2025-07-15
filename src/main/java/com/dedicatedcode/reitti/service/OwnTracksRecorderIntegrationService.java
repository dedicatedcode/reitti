package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OwnTracksRecorderIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    fromTime = integration.getLastSuccessfulFetch();
                } else {
                    fromTime = Instant.now().minus(5 , ChronoUnit.MINUTES);
                }
                
                // Fetch location data from OwnTracks Recorder
                List<OwntracksLocationRequest> locationData = fetchLocationData(integration, fromTime);
                
                if (!locationData.isEmpty()) {
                    // Convert to LocationPoints and filter valid ones
                    List<LocationDataRequest.LocationPoint> validPoints = new ArrayList<>();
                    
                    for (OwntracksLocationRequest owntracksData : locationData) {
                        if (owntracksData.isLocationUpdate()) {
                            LocationDataRequest.LocationPoint locationPoint = owntracksData.toLocationPoint();
                            if (locationPoint.getTimestamp() != null && locationPoint.getAccuracyMeters() != null) {
                                validPoints.add(locationPoint);
                            }
                        }
                    }
                    
                    if (!validPoints.isEmpty()) {
                        LocationDataEvent event = new LocationDataEvent(user.getUsername(), validPoints);
                        
                        rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE_NAME,
                            RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                            event
                        );
                        
                        totalLocationPoints += validPoints.size();
                        logger.info("Imported {} location points for user {}", validPoints.size(), user.getUsername());
                        
                        // Find the latest timestamp from the received data
                        Instant latestTimestamp = validPoints.stream()
                                .map(LocationDataRequest.LocationPoint::getTimestamp).filter(Objects::nonNull)
                                .map(Instant::parse)
                                .max(Instant::compareTo).orElse(null);

                        if (latestTimestamp != null) {
                            // Update lastSuccessfulFetch with the latest timestamp from the data
                            OwnTracksRecorderIntegration updatedIntegration = integration.withLastSuccessfulFetch(latestTimestamp);
                            jdbcService.update(updatedIntegration);
                        }

                    }
                }
                
            } catch (Exception e) {
                logger.error("Failed to import data for user {} from OwnTracks Recorder: {}", 
                           user.getUsername(), e.getMessage(), e);
            }
        }
        
        logger.debug("OwnTracks Recorder import completed: processed {} integrations, imported {} location points",
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

            String testUrl = normalizedBaseUrl + "/api/0/locations?user=%s&device=%s".formatted(username, deviceId);
            
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

    public void loadHistoricalData(User user) {
        Optional<OwnTracksRecorderIntegration> integrationOpt = jdbcService.findByUser(user);
        
        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            throw new IllegalStateException("No enabled OwnTracks Recorder integration found for user");
        }
        
        OwnTracksRecorderIntegration integration = integrationOpt.get();
        
        try {
            // First, fetch all recs for the user
            Set<YearMonth> availableMonths = fetchAvailableMonths(integration);
            
            if (availableMonths.isEmpty()) {
                logger.info("No historical data found for user {}", user.getUsername());
                return;
            }
            
            logger.info("Found {} months of historical data for user {}", availableMonths.size(), user.getUsername());
            
            int totalLocationPoints = 0;
            
            // For each month, fetch location data
            for (YearMonth month : availableMonths) {
                try {
                    List<OwntracksLocationRequest> monthlyLocationData = fetchLocationDataForMonth(integration, month);
                    
                    if (!monthlyLocationData.isEmpty()) {
                        // Convert to LocationPoints and filter valid ones
                        List<LocationDataRequest.LocationPoint> validPoints = new ArrayList<>();
                        
                        for (OwntracksLocationRequest owntracksData : monthlyLocationData) {
                            if (owntracksData.isLocationUpdate()) {
                                LocationDataRequest.LocationPoint locationPoint = owntracksData.toLocationPoint();
                                if (locationPoint.getTimestamp() != null && locationPoint.getAccuracyMeters() != null) {
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
                            logger.debug("Loaded {} location points for user {} from month {}", 
                                       validPoints.size(), user.getUsername(), month);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to load data for user {} from month {}: {}", 
                               user.getUsername(), month, e.getMessage(), e);
                    // Continue with other months
                }
            }
            
            logger.info("Loaded {} total historical location points for user {}", totalLocationPoints, user.getUsername());
            
        } catch (Exception e) {
            logger.error("Failed to load historical data for user {} from OwnTracks Recorder: {}", 
                       user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to load historical data: " + e.getMessage(), e);
        }
    }

    private List<OwntracksLocationRequest> fetchLocationData(OwnTracksRecorderIntegration integration, Instant fromTime) {
        try {
            String apiUrl;
            if (fromTime == null) {
                fromTime = Instant.ofEpochSecond(0);
            }
            LocalDateTime fromDate = fromTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
            String fromDateString = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            apiUrl = String.format("%s/api/0/locations?user=%s&device=%s&from=%s&limit=500", integration.getBaseUrl(), integration.getUsername(), integration.getDeviceId(), fromDateString);
            return fetchData(apiUrl);
        } catch (Exception e) {
            logger.error("Failed to fetch location data from OwnTracks Recorder: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<OwntracksLocationRequest> fetchData(String apiUrl) {
        logger.info("Fetching location data from: {}", apiUrl);

        ResponseEntity<OwntracksRecorderResponse> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            logger.debug("Successfully fetched {} location records from OwnTracks Recorder", response.getBody().data.size());
            return response.getBody().data;
        } else {
            logger.warn("Unexpected response from OwnTracks Recorder: {}", response.getStatusCode());
            return Collections.emptyList();
        }
    }

    private Set<YearMonth> fetchAvailableMonths(OwnTracksRecorderIntegration integration) {
        try {
            String recsUrl = String.format("%s/api/0/list?user=%s&device=%s",
                                         integration.getBaseUrl(), 
                                         integration.getUsername(), 
                                         integration.getDeviceId());
            
            logger.debug("Fetching available recs from: {}", recsUrl);
            
            ResponseEntity<OwntracksRecsResponse> response = restTemplate.exchange(
                    recsUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Set<YearMonth> months = new HashSet<>();
                Pattern datePattern = Pattern.compile("/(\\d{4})-(\\d{2})\\.rec$");
                
                for (String recPath : response.getBody().results) {
                    Matcher matcher = datePattern.matcher(recPath);
                    if (matcher.find()) {
                        int year = Integer.parseInt(matcher.group(1));
                        int month = Integer.parseInt(matcher.group(2));
                        months.add(YearMonth.of(year, month));
                    }
                }
                
                logger.debug("Extracted {} unique months from {} rec files", months.size(), response.getBody().results.size());
                return months;
            } else {
                logger.warn("Unexpected response when fetching recs: {}", response.getStatusCode());
                return Collections.emptySet();
            }
        } catch (Exception e) {
            logger.error("Failed to fetch available months from OwnTracks Recorder: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
    
    private List<OwntracksLocationRequest> fetchLocationDataForMonth(OwnTracksRecorderIntegration integration, YearMonth month) {
        try {
            LocalDateTime fromDate = month.atDay(1).atStartOfDay();
            LocalDateTime toDate = month.atEndOfMonth().atTime(23, 59, 59);
            
            String fromDateString = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String toDateString = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            String apiUrl = String.format("%s/api/0/locations?user=%s&device=%s&from=%s&to=%s&limit=10000", 
                                        integration.getBaseUrl(), 
                                        integration.getUsername(), 
                                        integration.getDeviceId(), 
                                        fromDateString, 
                                        toDateString);
            
            return fetchData(apiUrl);
        } catch (Exception e) {
            logger.error("Failed to fetch location data for month {}: {}", month, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class OwntracksRecsResponse {
        @JsonProperty
        private List<String> results;
    }

    private static class OwntracksRecorderResponse {
        @JsonProperty
        private int count;

        @JsonProperty
        private List<OwntracksLocationRequest> data;

        @JsonProperty
        private int status;

        @JsonProperty
        private String version;
    }
}
