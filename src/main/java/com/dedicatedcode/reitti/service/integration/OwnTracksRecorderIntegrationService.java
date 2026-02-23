package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.OwnTracksRecorderIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ImportProcessor;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
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
    private final RestTemplate restTemplate;
    private final ImportProcessor importBatchProcessor;

    public OwnTracksRecorderIntegrationService(OwnTracksRecorderIntegrationJdbcService jdbcService,
                                               UserJdbcService userJdbcService,
                                               ImportProcessor importBatchProcessor) {
        this.jdbcService = jdbcService;
        this.userJdbcService = userJdbcService;
        this.importBatchProcessor = importBatchProcessor;
        this.restTemplate = new RestTemplate();
    }

    @Scheduled(cron = "${reitti.imports.owntracks-recorder.schedule}")
    void importNewData() {
        logger.trace("Starting OwnTracks Recorder data import");
        
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
                    List<LocationPoint> validPoints = new ArrayList<>();
                    
                    for (OwntracksLocationRequest owntracksData : locationData) {
                        if (owntracksData.isLocationUpdate()) {
                            LocationPoint locationPoint = owntracksData.toLocationPoint();
                            if (locationPoint.getTimestamp() != null && locationPoint.getAccuracyMeters() != null) {
                                validPoints.add(locationPoint);
                            }
                        }
                    }
                    
                    if (!validPoints.isEmpty()) {
                        importBatchProcessor.processBatch(user, validPoints);
                        totalLocationPoints += validPoints.size();
                        logger.info("Imported {} location points for user {}", validPoints.size(), user.getUsername());
                        
                        // Find the latest timestamp from the received data
                        Instant latestTimestamp = validPoints.stream()
                                .map(LocationPoint::getTimestamp).filter(Objects::nonNull)
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
        
        logger.trace("OwnTracks Recorder import completed: processed {} integrations, imported {} location points",
                   processedIntegrations, totalLocationPoints);
    }

    public Optional<OwnTracksRecorderIntegration> getIntegrationForUser(User user) {
        return jdbcService.findByUser(user);
    }

    public OwnTracksRecorderIntegration saveIntegration(User user, String baseUrl, String username, String authUsername, String authPassword, String deviceId, boolean enabled) {
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
                    authUsername,
                    authPassword,
                    enabled,
                    existing.getLastSuccessfulFetch(), existing.getVersion());
            return jdbcService.update(updated);
        } else {
            OwnTracksRecorderIntegration newIntegration = new OwnTracksRecorderIntegration(
                    normalizedBaseUrl,
                    username.trim(),
                    deviceId.trim(),
                    enabled,
                    authUsername,
                    authPassword
            );
            return jdbcService.save(user, newIntegration);
        }
    }

    public boolean testConnection(String baseUrl, String username, String authUsername, String authPassword, String deviceId) {
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        String testUrl = normalizedBaseUrl + "/api/0/locations?user=%s&device=%s".formatted(username, deviceId);

        logger.debug("Testing OwnTracks Recorder connection to: {}", testUrl);

        HttpEntity<String> entity = createHttpEntityWithAuth(authUsername, authPassword);
        ResponseEntity<String> response = restTemplate.exchange(testUrl, HttpMethod.GET, entity, String.class);

        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        boolean isSuccessful = statusCode.is2xxSuccessful() ||
                statusCode == HttpStatus.UNAUTHORIZED ||
                statusCode == HttpStatus.FORBIDDEN;

        logger.debug("OwnTracks Recorder connection test result: {} (status: {})", isSuccessful, statusCode);
        return isSuccessful;

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
                        List<LocationPoint> validPoints = new ArrayList<>();
                        
                        for (OwntracksLocationRequest owntracksData : monthlyLocationData) {
                            if (owntracksData.isLocationUpdate()) {
                                LocationPoint locationPoint = owntracksData.toLocationPoint();
                                if (locationPoint.getTimestamp() != null && locationPoint.getAccuracyMeters() != null) {
                                    validPoints.add(locationPoint);
                                }
                            }
                        }
                        
                        if (!validPoints.isEmpty()) {
                            importBatchProcessor.processBatch(user, validPoints);
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
            if (fromTime == null) {
                fromTime = Instant.ofEpochSecond(0);
            }
            return fetchAllLocationDataWithPagination(integration, fromTime, null, 10000);
        } catch (Exception e) {
            logger.error("Failed to fetch location data from OwnTracks Recorder: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<OwntracksLocationRequest> fetchAllLocationDataWithPagination(OwnTracksRecorderIntegration integration, 
                                                                               Instant fromTime, Instant toTime, int limit) {
        List<OwntracksLocationRequest> allData = new ArrayList<>();
        Instant currentToTime = toTime;
        
        while (true) {
            LocalDateTime fromDate = fromTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
            String fromDateString = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            StringBuilder apiUrlBuilder = new StringBuilder();
            apiUrlBuilder.append(String.format("%s/api/0/locations?user=%s&device=%s&from=%s&limit=%d", 
                integration.getBaseUrl(), 
                integration.getUsername(), 
                integration.getDeviceId(), 
                fromDateString, 
                limit));
            
            // Add 'to' parameter if specified (for historical data queries)
            if (currentToTime != null) {
                LocalDateTime toDate = currentToTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
                String toDateString = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                apiUrlBuilder.append("&to=").append(toDateString);
            }
            
            String apiUrl = apiUrlBuilder.toString();
            List<OwntracksLocationRequest> pageData = fetchData(apiUrl, integration);
            
            if (pageData.isEmpty()) {
                break;
            }
            
            allData.addAll(pageData);
            
            logger.info("Returned {} points from OwnTracks; most recent timestamp {}", pageData.size(), pageData.get(0).getTimestamp());
            // If we received less than limit records, we've reached the end
            if (pageData.size() < limit) {
                break;
            }
            
            // Find the most recent timestamp in this batch (last item, since OwnTracks returns newest to oldest) - this will be the new "to"-timestamp.
            Instant newestTimestamp = pageData.isEmpty() ? null : 
               Instant.ofEpochSecond(pageData.get(limit-1).getTimestamp());
            
            if (newestTimestamp == null) {
                // No valid timestamps found, stop pagination
                break;
            }
            
            // Set currentToTime to the newest timestamp for the next request
            currentToTime = newestTimestamp;
        }
        
        return allData;
    }

    private List<OwntracksLocationRequest> fetchData(String apiUrl, OwnTracksRecorderIntegration integration) {
        logger.info("Fetching location data from: {}", apiUrl);

        HttpEntity<String> entity = createHttpEntityWithAuth(integration.getAuthUsername(), integration.getAuthPassword());
        ResponseEntity<OwntracksRecorderResponse> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
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
            
            HttpEntity<String> entity = createHttpEntityWithAuth(integration.getAuthUsername(), integration.getAuthPassword());
            ResponseEntity<OwntracksRecsResponse> response = restTemplate.exchange(
                    recsUrl,
                    HttpMethod.GET,
                    entity,
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
            
            Instant fromInstant = fromDate.toInstant(ZoneOffset.UTC);
            Instant toInstant = toDate.toInstant(ZoneOffset.UTC);
            
            return fetchAllLocationDataWithPagination(integration, fromInstant, toInstant, 10000);
        } catch (Exception e) {
            logger.error("Failed to fetch location data for month {}: {}", month, e.getMessage());
            return Collections.emptyList();
        }
    }

    private HttpEntity<String> createHttpEntityWithAuth(String authUsername, String authPassword) {
        HttpHeaders headers = new HttpHeaders();
        
        if (authUsername != null && !authUsername.trim().isEmpty() && 
            authPassword != null && !authPassword.trim().isEmpty()) {
            String auth = authUsername + ":" + authPassword;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);
        }
        
        return new HttpEntity<>(headers);
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
