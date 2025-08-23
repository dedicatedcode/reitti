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

import java.util.Collections;
import java.util.List;
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
        assertThat(geocodeResult.label()).isEqualTo("Test Location");
        assertThat(geocodeResult.street()).isEqualTo("Test Street");
        assertThat(geocodeResult.city()).isEqualTo("Test City");
        assertThat(geocodeResult.district()).isEqualTo("Test District");
        
        verify(geocodeServiceJdbcService).save(any(RemoteGeocodeService.class));
    }

    @Test
    void shouldReturnCorrectGeoCodeResultFromRemoteService() {
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
                {"place_id":309281591,"licence":"Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright","osm_type":"way","osm_id":555816145,"lat":"38.9763500","lon":"-94.5953511","class":"amenity","type":"fast_food","place_rank":30,"importance":7.305638208586279e-05,"addresstype":"amenity","name":"McDonald's","display_name":"McDonald's, 8326, Wornall Road, Waldo, Kansas City, Jackson County, Missouri, 64114, United States","address":{"amenity":"McDonald's","house_number":"8326","road":"Wornall Road","neighbourhood":"Waldo","city":"Kansas City","county":"Jackson County","state":"Missouri","ISO3166-2-lvl4":"US-MO","postcode":"64114","country":"United States","country_code":"us"},"boundingbox":["38.9762717","38.9764468","-94.5955208","-94.5951802"]}
                """;

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Optional<GeocodeResult> result = geocodeServiceManager.reverseGeocode(latitude, longitude);

        // Then
        assertThat(result).isPresent();
        GeocodeResult geocodeResult = result.get();
        assertThat(geocodeResult.label()).isEqualTo("McDonald's");
        assertThat(geocodeResult.street()).isEqualTo("Wornall Road 8326");
        assertThat(geocodeResult.city()).isEqualTo("Kansas City");
        assertThat(geocodeResult.district()).isEqualTo("Waldo");

        verify(geocodeServiceJdbcService).save(any(RemoteGeocodeService.class));
    }

    @Test
    void shouldReturnCorrectGeoCodeResultFromGeoCodeJsonRemoteService() {
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
                                            "type": "FeatureCollection",
                                            "geocoding": {
                                              "version": "0.1.0",
                                              "attribution": "Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright",
                                              "licence": "ODbL",
                                              "query": ""
                                            },
                                            "features": [
                                              {
                                                "type": "Feature",
                                                "properties": {
                                                  "geocoding": {
                                                    "place_id": 309600843,
                                                    "osm_type": "way",
                                                    "osm_id": 555816145,
                                                    "osm_key": "amenity",
                                                    "osm_value": "fast_food",
                                                    "type": "house",
                                                    "accuracy": 0,
                                                    "label": "McDonald's, 8326, Wornall Road, Waldo, Kansas City, Jackson County, Missouri, 64114, United States",
                                                    "name": "McDonald's",
                                                    "housenumber": "8326",
                                                    "postcode": "64114",
                                                    "street": "Wornall Road",
                                                    "locality": "Waldo",
                                                    "city": "Kansas City",
                                                    "county": "Jackson County",
                                                    "state": "Missouri",
                                                    "country": "United States",
                                                    "country_code": "us",
                                                    "admin": {
                                                      "level8": "Kansas City",
                                                      "level6": "Jackson County",
                                                      "level4": "Missouri"
                                                    }
                                                  }
                                                },
                                                "geometry": {
                                                  "type": "Point",
                                                  "coordinates": [
                                                    -94.59535111452982,
                                                    38.97635
                                                  ]
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
        assertThat(geocodeResult.label()).isEqualTo("McDonald's");
        assertThat(geocodeResult.street()).isEqualTo("Wornall Road 8326");
        assertThat(geocodeResult.city()).isEqualTo("Kansas City");
        assertThat(geocodeResult.district()).isEqualTo("Waldo");

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
        assertThat(geocodeResult.label()).isEqualTo("Photon Location");
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
