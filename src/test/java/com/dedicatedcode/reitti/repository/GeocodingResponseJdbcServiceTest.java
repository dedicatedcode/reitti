package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.GeocodingResponse;
import com.dedicatedcode.reitti.model.SignificantPlace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GeocodingResponseJdbcServiceTest {

    @Autowired
    private GeocodingResponseJdbcService geocodingResponseJdbcService;

    @Test
    void shouldInsertAndFindGeocodingResponse() {
        // Given
        SignificantPlace place = new SignificantPlace(
                1L, "Test Place", "Test Address", "de",
                53.863149, 10.700927, null, SignificantPlace.PlaceType.HOME,
                true, null
        );
        
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
        GeocodingResponse foundResponse = found.get(0);
        assertThat(foundResponse.getSignificantPlaceId()).isEqualTo(place.getId());
        assertThat(foundResponse.getRawData()).isEqualTo("{\"results\": []}");
        assertThat(foundResponse.getProviderName()).isEqualTo("test-provider");
        assertThat(foundResponse.getStatus()).isEqualTo(GeocodingResponse.GeocodingStatus.SUCCESS);
        assertThat(foundResponse.getErrorDetails()).isNull();
    }

    @Test
    void shouldReturnEmptyListWhenNoResponseFound() {
        // Given
        SignificantPlace place = new SignificantPlace(
                999L, "Non-existent Place", "Non-existent Address", "de",
                53.863149, 10.700927, null, SignificantPlace.PlaceType.HOME,
                true, null
        );

        // When
        List<GeocodingResponse> found = geocodingResponseJdbcService.findBySignificantPlace(place);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldInsertResponseWithError() {
        // Given
        SignificantPlace place = new SignificantPlace(
                5L, "Error Place", "Error Address", "de",
                53.863149, 10.700927, null, SignificantPlace.PlaceType.WORK,
                true, null
        );
        
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
        GeocodingResponse foundResponse = found.get(0);
        assertThat(foundResponse.getStatus()).isEqualTo(GeocodingResponse.GeocodingStatus.ERROR);
        assertThat(foundResponse.getErrorDetails()).isEqualTo("Network timeout");
        assertThat(foundResponse.getRawData()).isNull();
    }
}
