package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.geocoding.services.ResultHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
    private final I18nService i18nService;
    private final int maxErrors;

    public DefaultGeocodeServiceManager(GeocodeServiceJdbcService geocodeServiceJdbcService,
                                        GeocodingResponseJdbcService geocodingResponseJdbcService,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        List<ResultHandler> resultHandlers,
                                        I18nService i18nService,
                                        @Value("${reitti.geocoding.max-errors}") int maxErrors) {
        this.geocodeServiceJdbcService = geocodeServiceJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.resultHandlers = resultHandlers;
        this.i18nService = i18nService;
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
    public Map<GeocoderType, List<GeocodeResult>> reverseGeocodeAll(SignificantPlace significantPlace) {
        double latitude = significantPlace.getLatitudeCentroid();
        double longitude = significantPlace.getLongitudeCentroid();
        List<GeocodeService> availableServices = geocodeServiceJdbcService.findByEnabledTrueOrderByPriority();
        Map<GeocoderType, List<GeocodeResult>> results = new HashMap<>();
        if (availableServices.isEmpty()) {
            logger.warn("No enabled geocoding services available");
            return Map.of();
        }

        for (GeocodeService service : availableServices) {
            List<GeocodeResult> serviceResults = performGeocode(service, latitude, longitude, significantPlace, true);
            if (!serviceResults.isEmpty()) {
                results.computeIfAbsent(service.getType(), _ -> new ArrayList<>())
                        .addAll(serviceResults);
            }
        }
        return results;
    }

    @Override
    public Map<String, Object> test(GeocodeService service, double testLat, double testLng) {
        try {
            String response = callService(service, testLat, testLng);
            extractGeoCodeResult(service.getType(), response);
            return Map.of("success", true, "message", i18nService.translate("geocoding.test.success"));

        } catch (Exception e) {
            logger.warn("Failed to call geocoding service [{}]: [{}]", service.getName(), e.getMessage());
            return Map.of("success", false, "message", i18nService.translate("geocoding.test.error",e.getMessage()));

        }
    }

    private Optional<GeocodeResult> callGeocodeService(List<? extends GeocodeService> availableServices, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        List<? extends GeocodeService> shuffledServices = new ArrayList<>(availableServices);
        Collections.shuffle(shuffledServices);

        for (GeocodeService service : shuffledServices) {
            try {
                List<GeocodeResult> result = performGeocode(service, latitude, longitude, significantPlace, recordResponse);
                if (!result.isEmpty()) {
                    if (recordResponse) {
                        recordSuccess(service);
                    }
                    return result.stream().findFirst();
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

    private List<GeocodeResult> performGeocode(GeocodeService service, double latitude, double longitude, SignificantPlace significantPlace, boolean recordResponse) {
        try {
            String response = callService(service, latitude, longitude);

            List<GeocodeResult> geocodeResult = extractGeoCodeResult(service.getType(), response);
            if (recordResponse && !geocodeResult.isEmpty()) {
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
            recordError(service);
        }
        return Collections.emptyList();
    }

    private String callService(GeocodeService service, double latitude, double longitude) {
        String url = service.getUrlTemplate()
                .replace("{lat}", String.valueOf(latitude))
                .replace("{lng}", String.valueOf(longitude));

        logger.info("Geocoding with service [{}] using URL: [{}]", service.getName(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Reitti/1.0 (+https://github.com/dedicatedcode/reitti; contact: reitti@dedicatedcode.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            throw new IllegalStateException("Service responded with status code [" + responseEntity.getStatusCode() + "]");
        }
    }

    private List<GeocodeResult> extractGeoCodeResult(GeocoderType type, String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        List<GeocodeResult> results = new ArrayList<>();
        this.resultHandlers.stream().filter(rh -> rh.canHandle(type)).forEach(rh -> results.addAll(rh.handle(root)));
        return results;
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
