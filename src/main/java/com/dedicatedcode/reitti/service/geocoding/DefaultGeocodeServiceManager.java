package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.service.geocoding.services.ResultHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultGeocodeServiceManager implements GeocodeServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGeocodeServiceManager.class);

    private final GeocodeServiceJdbcService geocodeServiceJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final List<ResultHandler> resultHandlers;
    private final int maxErrors;

    public DefaultGeocodeServiceManager(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                        GeocodingResponseJdbcService geocodingResponseJdbcService,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        List<ResultHandler> resultHandlers,
                                        @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.resultHandlers = resultHandlers;
        this.maxErrors = maxErrors;
    }

    @Transactional
    @Override
    public Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse) {
        double latitude = significantPlace.getLatitudeCentroid();
        double longitude = significantPlace.getLongitudeCentroid();
        List<GeocodeService> availableServices = geocodeServiceJdbcService.findByEnabledTrueOrderByPriority();

        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Optional.empty();
        }

        Map<Integer, List<GeocodeService>> servicesByPriority = availableServices.stream()
                .collect(Collectors.groupingBy(GeocodeService::getPriority, TreeMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<GeocodeService>> entry : servicesByPriority.entrySet()) {
            List<GeocodeService> priorityGroup = entry.getValue();
            Optional<GeocodeResult> result = callGeocodeService(priorityGroup, latitude, longitude, significantPlace, recordResponse);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    @Override
    public GeocodeResult test(GeocodeService service, double testLat, double testLng) {
        Optional<GeocodeResult> geocodeResult = performGeocode(service, testLat, testLng, null, false);
        return geocodeResult.orElseThrow(() -> new RuntimeException("Failed to call geocoding service: " + service.getName()));
    }

    private Optional<GeocodeResult> callGeocodeService(List<? extends GeocodeService> availableServices, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        List<? extends GeocodeService> shuffledServices = new ArrayList<>(availableServices);
        Collections.shuffle(shuffledServices);

        for (GeocodeService service : shuffledServices) {
            try {
                Optional<GeocodeResult> result = performGeocode(service, latitude, longitude, significantPlace, recordResponse);
                if (result.isPresent()) {
                    if (recordResponse) {
                        recordSuccess(service);
                    }
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Geocoding failed for service [{}]: [{}]", service.getName(), e.getMessage());
                if (recordResponse) {
                    recordError(service);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<GeocodeResult> performGeocode(GeocodeService service, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        String url = service.getUrlTemplate()
                .replace("{lat}", String.valueOf(latitude))
                .replace("{lng}", String.valueOf(longitude));

        logger.info("Geocoding with service [{}] using URL: [{}]", service.getName(), url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            Optional<GeocodeResult> geocodeResult = extractGeoCodeResult(service.getType(), response);
            if (recordResponse && geocodeResult.isPresent()) {
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        response,
                        service.getName(),
                        Instant.now(),
                        GeocodingResponse.GeocodingStatus.SUCCESS,
                        null
                ));
            } else if (recordResponse){
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        response,
                        service.getName(),
                        Instant.now(),
                        GeocodingResponse.GeocodingStatus.ZERO_RESULTS,
                        null
                ));
            }
            return geocodeResult;

        } catch (Exception e) {
            logger.error("Failed to call geocoding service [{}]: [{}]", service.getName(), e.getMessage());
            GeocodingResponse.GeocodingStatus status = determineErrorStatus(e);
            if (recordResponse) {
                geocodingResponseJdbcService.insert(new GeocodingResponse(
                        significantPlace.getId(),
                        null,
                        service.getName(),
                        Instant.now(),
                        status,
                        e.getMessage()
                ));
            }
            throw new RuntimeException("Failed to call geocoding service: " + e.getMessage(), e);
        }
    }

    private Optional<GeocodeResult> extractGeoCodeResult(GeocoderType type, String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        return this.resultHandlers.stream()
                .filter(rh -> rh.canHandle(type))
                .map(rh -> rh.handle(root))
                .findFirst().orElseThrow();
    }

    private void recordSuccess(GeocodeService service) {
        geocodeServiceJdbcService.save(service.withLastUsed(Instant.now()));
    }

    private GeocodingResponse.GeocodingStatus determineErrorStatus(Exception e) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains("rate limit") || message.contains("too many requests") || 
            message.contains("429") || message.contains("quota exceeded")) {
            return GeocodingResponse.GeocodingStatus.RATE_LIMITED;
        }
        
        if (message.contains("invalid") || message.contains("bad request") || 
            message.contains("400") || message.contains("malformed")) {
            return GeocodingResponse.GeocodingStatus.INVALID_REQUEST;
        }
        
        return GeocodingResponse.GeocodingStatus.ERROR;
    }

    private void recordError(GeocodeService service) {
            GeocodeService update = service
                    .withIncrementedErrorCount()
                    .withLastError(Instant.now());

            if (update.getErrorCount() >= maxErrors) {
                update = update.withEnabled(false);
                logger.warn("Geocoding service [{}] disabled due to too many errors ([{}]/[{}])",
                        update.getName(), update.getErrorCount(), maxErrors);
            }

            geocodeServiceJdbcService.save(update);
    }
}
