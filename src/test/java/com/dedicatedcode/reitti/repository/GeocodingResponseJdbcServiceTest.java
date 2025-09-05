package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ActiveProfiles("test")
@Transactional
class GeocodingResponseJdbcServiceTest {

    @Autowired
    private GeocodingResponseJdbcService geocodingResponseJdbcService;
    @Autowired
    private SignificantPlaceJdbcService placeService;
    @Autowired
    private TestingService testingService;

    @Test
    void shouldInsertAndFindGeocodingResponse() {
        // Given

        double latitudeCentroid = 53.863149;
        double longitudeCentroid = 10.700927;
        SignificantPlace place = placeService.create(testingService.admin(), SignificantPlace.create(latitudeCentroid, longitudeCentroid));
        
        GeocodingResponse response = new GeocodingResponse(
            place.getId(),
            "{\"results\": []}",
            "test-provider",
            Instant.now(),
            GeocodingResponse.GeocodingStatus.SUCCESS,
            null
        );

        // When
        geocodingResponseJdbcService.insert(response);
        List<GeocodingResponse> found = geocodingResponseJdbcService.findBySignificantPlace(place);

        // Then
        assertThat(found).hasSize(1);
        GeocodingResponse foundResponse = found.getFirst();
        assertThat(foundResponse.getSignificantPlaceId()).isEqualTo(place.getId());
        assertThat(foundResponse.getRawData()).isEqualTo("{\"results\": []}");
        assertThat(foundResponse.getProviderName()).isEqualTo("test-provider");
        assertThat(foundResponse.getStatus()).isEqualTo(GeocodingResponse.GeocodingStatus.SUCCESS);
        assertThat(foundResponse.getErrorDetails()).isNull();
    }

    @Test
    void shouldReturnEmptyListWhenNoResponseFound() {
        // Given

        double latitudeCentroid = 53.863149;
        double longitudeCentroid = 10.700927;
        SignificantPlace place = placeService.create(testingService.admin(), SignificantPlace.create(latitudeCentroid, longitudeCentroid));

        // When
        List<GeocodingResponse> found = geocodingResponseJdbcService.findBySignificantPlace(place);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldInsertResponseWithError() {
        // Given

        double latitudeCentroid = 53.863149;
        double longitudeCentroid = 10.700927;
        SignificantPlace place = placeService.create(testingService.admin(), SignificantPlace.create(latitudeCentroid, longitudeCentroid));

        GeocodingResponse response = new GeocodingResponse(
            place.getId(),
            null,
            "error-provider",
            Instant.now(),
            GeocodingResponse.GeocodingStatus.ERROR,
            "Network timeout"
        );

        // When
        geocodingResponseJdbcService.insert(response);
        List<GeocodingResponse> found = geocodingResponseJdbcService.findBySignificantPlace(place);

        // Then
        assertThat(found).hasSize(1);
        GeocodingResponse foundResponse = found.getFirst();
        assertThat(foundResponse.getStatus()).isEqualTo(GeocodingResponse.GeocodingStatus.ERROR);
        assertThat(foundResponse.getErrorDetails()).isEqualTo("Network timeout");
        assertThat(foundResponse.getRawData()).isNull();
    }
}
