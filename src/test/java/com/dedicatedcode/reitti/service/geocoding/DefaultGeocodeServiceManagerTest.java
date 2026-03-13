package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.repository.GeocodingResponseJdbcService;
import com.dedicatedcode.reitti.service.geocoding.services.PaikkaResultHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultGeocodeServiceManagerTest {

    @Mock
    private GeocodeServiceJdbcService geocodeServiceJdbcService;

    @Mock
    private GeocodingResponseJdbcService geocodingResponseJdbcService;

    @Mock
    private RestTemplate restTemplate;

    private DefaultGeocodeServiceManager geocodeServiceManager;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        geocodeServiceManager = new DefaultGeocodeServiceManager(
                geocodeServiceJdbcService,
                geocodingResponseJdbcService,
                restTemplate,
                objectMapper,
                Collections.singletonList(new PaikkaResultHandler()),
                3
        );
    }

    @Test
    void shouldReturnEmptyWhenNoServicesAvailable() {
        // Given
        when(geocodeServiceJdbcService.findByEnabledTrueOrderByPriority())
                .thenReturn(Collections.emptyList());

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(SignificantPlace.create(53.863149, 10.700927), true);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleServiceErrorAndRecordIt() {
        // Given
        double latitude = 53.863149;
        double longitude = 10.700927;
        
        GeocodeService service = new GeocodeService(
                1L, "Failing Service", "http://fail.com?lat={lat}&lng={lng}",
                true, 0, null, null, GeocoderType.PAIKKA, Map.of(), 1,
                1L);
        
        when(geocodeServiceJdbcService.findByEnabledTrueOrderByPriority())
                .thenReturn(List.of(service));
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(SignificantPlace.create(latitude, longitude), true);

        // Then
        assertThat(result).isEmpty();
        verify(geocodeServiceJdbcService).save(any(GeocodeService.class));
    }

    @Test
    void shouldTryHigherPriorityServicesFirst() {
        // Given
        GeocodeService priority1 = new GeocodeService(1L, "P1", "http://p1.com?lat={lat}&lng={lng}", true, 0, null, null, GeocoderType.PAIKKA, Map.of(), 1, 1L);
        GeocodeService priority2 = new GeocodeService(2L, "P2", "http://p2.com?lat={lat}&lng={lng}", true, 0, null, null, GeocoderType.PAIKKA, Map.of(), 2, 1L);

        when(geocodeServiceJdbcService.findByEnabledTrueOrderByPriority()).thenReturn(List.of(priority1, priority2));
        
        // Mock successful response for P1
        String successJson = "{\"results\": [{\"display_name\": \"Found\", \"type\": \"building\", \"address\": {\"street\": \"S\", \"city\": \"C\"}, \"hierarchy\": [{\"country_code\": \"FI\"}]}]}";
        when(restTemplate.exchange(startsWith("http://p1.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(successJson));

        // When
        geocodeServiceManager.reverseGeocode(SignificantPlace.create(53.0, 10.0), true);

        // Then
        verify(restTemplate).exchange(startsWith("http://p1.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, Mockito.never()).exchange(startsWith("http://p2.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldMoveToNextPriorityIfFirstGroupFails() {
        // Given
        GeocodeService priority1 = new GeocodeService(1L, "P1", "http://p1.com?lat={lat}&lng={lng}", true, 0, null, null, GeocoderType.PAIKKA, Map.of(), 1, 1L);
        GeocodeService priority2 = new GeocodeService(2L, "P2", "http://p2.com?lat={lat}&lng={lng}", true, 0, null, null, GeocoderType.PAIKKA, Map.of(), 2, 1L);

        when(geocodeServiceJdbcService.findByEnabledTrueOrderByPriority()).thenReturn(List.of(priority1, priority2));

        // P1 fails
        when(restTemplate.exchange(startsWith("http://p1.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenThrow(new RuntimeException("P1 Down"));
        
        // P2 succeeds
        String successJson = "{\"results\": [{\"display_name\": \"Found\", \"type\": \"building\", \"address\": {\"street\": \"S\", \"city\": \"C\"}, \"hierarchy\": [{\"country_code\": \"FI\"}]}]}";
        when(restTemplate.exchange(startsWith("http://p2.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(successJson));

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(SignificantPlace.create(53.0, 10.0), true);

        // Then
        assertThat(result).isPresent();
        InOrder inOrder = Mockito.inOrder(restTemplate);
        inOrder.verify(restTemplate).exchange(startsWith("http://p1.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        inOrder.verify(restTemplate).exchange(startsWith("http://p2.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
}
