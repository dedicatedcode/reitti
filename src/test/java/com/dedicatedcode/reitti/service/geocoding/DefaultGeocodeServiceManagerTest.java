package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.RemoteGeocodeService;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultGeocodeServiceManagerTest {

    @Mock
    private GeocodeServiceJdbcService geocodeServiceJdbcService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GeocodeService fixedGeocodeService;

    private DefaultGeocodeServiceManager geocodeServiceManager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geocodeServiceManager = new DefaultGeocodeServiceManager(
                geocodeServiceJdbcService,
                Collections.emptyList(),
                restTemplate,
                objectMapper,
                3
        );
    }

    @Test
    void shouldReturnEmptyWhenNoServicesAvailable() {
        // Given
        when(geocodeServiceJdbcService.findByEnabledTrueOrderByLastUsedAsc())
                .thenReturn(Collections.emptyList());

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(53.863149, 10.700927);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnGeocodeResultFromRemoteService() {
        // Given
        double latitude = 53.863149;
        double longitude = 10.700927;
        
        RemoteGeocodeService service = new RemoteGeocodeService(
                1L, "Test Service", "http://test.com?lat={lat}&lng={lng}", 
                true, 0, null, null, 1L
        );
        
        when(geocodeServiceJdbcService.findByEnabledTrueOrderByLastUsedAsc())
                .thenReturn(List.of(service));
        
        String mockResponse = """
                {
                    "features": [
                        {
                            "properties": {
                                "name": "Test Location",
                                "address": {
                                    "road": "Test Street",
                                    "city": "Test City",
                                    "city_district": "Test District"
                                }
                            }
                        }
                    ]
                }
                """;
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(latitude, longitude);

        // Then
        assertThat(result).isPresent();
        GeocodeResult geocodeResult = result.get();
        assertThat(geocodeResult.name()).isEqualTo("Test Location");
        assertThat(geocodeResult.street()).isEqualTo("Test Street");
        assertThat(geocodeResult.city()).isEqualTo("Test City");
        assertThat(geocodeResult.district()).isEqualTo("Test District");
        
        verify(geocodeServiceJdbcService).save(any(RemoteGeocodeService.class));
    }

    @Test
    void shouldUseFixedGeocodeServiceWhenAvailable() {
        // Given
        double latitude = 53.863149;
        double longitude = 10.700927;
        
        DefaultGeocodeServiceManager managerWithFixedService = new DefaultGeocodeServiceManager(
                geocodeServiceJdbcService,
                List.of(fixedGeocodeService),
                restTemplate,
                objectMapper,
                3
        );
        
        when(fixedGeocodeService.getName()).thenReturn("Photon Service");
        when(fixedGeocodeService.getUrlTemplate()).thenReturn("http://photon.test?lat={lat}&lng={lng}");
        
        String photonResponse = """
                {
                    "features": [
                        {
                            "properties": {
                                "name": "Photon Location",
                                "street": "Photon Street",
                                "city": "Photon City",
                                "district": "Photon District",
                                "housenumber": "123",
                                "postcode": "12345"
                            }
                        }
                    ]
                }
                """;
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(photonResponse);

        // When
        Optional<GeocodeResult> result = managerWithFixedService.reverseGeocode(latitude, longitude);

        // Then
        assertThat(result).isPresent();
        GeocodeResult geocodeResult = result.get();
        assertThat(geocodeResult.name()).isEqualTo("Photon Location");
        assertThat(geocodeResult.street()).isEqualTo("Photon Street");
        assertThat(geocodeResult.city()).isEqualTo("Photon City");
        assertThat(geocodeResult.district()).isEqualTo("Photon District");
        assertThat(geocodeResult.houseNumber()).isEqualTo("123");
        assertThat(geocodeResult.postcode()).isEqualTo("12345");
    }

    @Test
    void shouldHandleServiceErrorAndRecordIt() {
        // Given
        double latitude = 53.863149;
        double longitude = 10.700927;
        
        RemoteGeocodeService service = new RemoteGeocodeService(
                1L, "Failing Service", "http://fail.com?lat={lat}&lng={lng}", 
                true, 0, null, null, 1L
        );
        
        when(geocodeServiceJdbcService.findByEnabledTrueOrderByLastUsedAsc())
                .thenReturn(List.of(service));
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(latitude, longitude);

        // Then
        assertThat(result).isEmpty();
        verify(geocodeServiceJdbcService).save(any(RemoteGeocodeService.class));
    }
}
