package com.dedicatedcode.reitti.service.integration.mqtt;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnTracksProcessorTest {

    @Mock
    private LocationBatchingService locationBatchingService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OwnTracksProcessor ownTracksProcessor;

    @Captor
    private ArgumentCaptor<LocationPoint> locationPointCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "testuser", "password", "Test User", null, null, null, 1L);
    }

    @Test
    void shouldProcessValidLocationUpdate() throws Exception {
        // Given
        long epochSecond = 1672574400L; // 2023-01-01T12:00:00Z in epoch seconds
        String validJson = "{\"_type\":\"location\",\"tst\":" + epochSecond + ",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("location");
        request.setTimestamp(epochSecond);
        request.setLatitude(53.863149);
        request.setLongitude(10.700927);
        request.setAccuracy(10.0);

        LocationPoint expectedLocationPoint = new LocationPoint();
        expectedLocationPoint.setTimestamp(Instant.ofEpochSecond(epochSecond).toString());
        expectedLocationPoint.setLatitude(53.863149);
        expectedLocationPoint.setLongitude(10.700927);
        expectedLocationPoint.setAccuracyMeters(10.0);

        when(objectMapper.readValue(validJson, OwntracksLocationRequest.class)).thenReturn(request);

        // When
        ownTracksProcessor.process(testUser, validJson.getBytes());

        // Then
        verify(locationBatchingService).addLocationPoint(eq(testUser), locationPointCaptor.capture());
        LocationPoint capturedPoint = locationPointCaptor.getValue();
        assertEquals(expectedLocationPoint.getTimestamp(), capturedPoint.getTimestamp());
        assertEquals(expectedLocationPoint.getLatitude(), capturedPoint.getLatitude());
        assertEquals(expectedLocationPoint.getLongitude(), capturedPoint.getLongitude());
        assertEquals(expectedLocationPoint.getAccuracyMeters(), capturedPoint.getAccuracyMeters());
    }

    @Test
    void shouldIgnoreNonLocationMessage() throws Exception {
        // Given
        String nonLocationJson = "{\"_type\":\"transition\",\"tst\":1672574400}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("transition");

        when(objectMapper.readValue(nonLocationJson, OwntracksLocationRequest.class)).thenReturn(request);

        // When
        ownTracksProcessor.process(testUser, nonLocationJson.getBytes());

        // Then
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }

    @Test
    void shouldIgnoreLocationWithNullTimestamp() throws Exception {
        // Given
        String invalidJson = "{\"_type\":\"location\",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("location");
        request.setLatitude(53.863149);
        request.setLongitude(10.700927);
        request.setAccuracy(10.0);
        // timestamp is null

        when(objectMapper.readValue(invalidJson, OwntracksLocationRequest.class)).thenReturn(request);

        // When
        ownTracksProcessor.process(testUser, invalidJson.getBytes());

        // Then
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }

    @Test
    void shouldHandleProcessingErrorGracefully() throws Exception {
        // Given
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, OwntracksLocationRequest.class))
            .thenThrow(new RuntimeException("JSON parsing error"));

        // When/Then
        assertDoesNotThrow(() -> ownTracksProcessor.process(testUser, invalidJson.getBytes()));
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }
}
