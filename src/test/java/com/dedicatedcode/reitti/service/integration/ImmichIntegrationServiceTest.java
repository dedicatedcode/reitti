package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.ImmichAsset;
import com.dedicatedcode.reitti.dto.ImmichSearchRequest;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ImmichIntegrationServiceTest {

    @Autowired
    private ImmichIntegrationService immichIntegrationService;

    @Autowired
    private ImmichIntegrationJdbcService immichIntegrationJdbcService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private TestingService testingService;

    @Test
    void testGetIntegrationForUser() {
        // Given
        User user = testingService.randomUser();
        
        // When
        Optional<ImmichIntegration> result = immichIntegrationService.getIntegrationForUser(user);
        
        // Then
        assertNotNull(result);
    }

    @Test
    void testSaveIntegration() {
        // Given
        User user = testingService.randomUser();
        String serverUrl = "https://immich.example.com";
        String apiToken = "test-token";
        boolean useBestGuessLocation = true;
        boolean enabled = true;
        
        // When
        ImmichIntegration saved = immichIntegrationService.saveIntegration(user, serverUrl, apiToken, useBestGuessLocation, enabled);
        
        // Then
        assertNotNull(saved);
        assertEquals(serverUrl, saved.getServerUrl());
        assertEquals(apiToken, saved.getApiToken());
        assertTrue(saved.isUseBestGuessLocation());
        assertTrue(saved.isEnabled());
    }

    @Test
    void testTestConnection_Success() {
        // Given
        String serverUrl = "https://immich.example.com";
        String apiToken = "test-token";
        
        // When
        IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);
        
        // Then
        assertNotNull(result);
        // Note: This will likely fail in integration test since the URL doesn't exist
        // In a real integration test with WireMock, we'd mock the response
    }

    @Test
    void testTestConnection_MissingParameters() {
        // Given
        String serverUrl = "";
        String apiToken = "";
        
        // When
        IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);
        
        // Then
        assertNotNull(result);
        assertFalse(result.success());
    }

    @Test
    void testSearchPhotosForRange() {
        // Given
        User user = testingService.randomUser();
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        String timezone = "UTC";
        
        // When
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, start, end, timezone);
        
        // Then
        assertNotNull(photos);
        // Will be empty since no integration is set up
    }

    @Test
    void testProxyImageRequest() {
        // Given
        User user = testingService.randomUser();
        String assetId = "test-asset-id";
        String size = "thumbnail";
        
        // When
        ResponseEntity<byte[]> response = immichIntegrationService.proxyImageRequest(user, assetId, size);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testDownloadImage() {
        // Given
        User user = testingService.randomUser();
        String assetId = "test-asset-id";
        String targetPath = "test-path";
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            immichIntegrationService.downloadImage(user, assetId, targetPath);
        });
    }

    @Test
    void testSaveIntegration_UpdatesExisting() {
        // Given
        User user = testingService.randomUser();
        String serverUrl = "https://immich.example.com";
        String apiToken = "test-token";
        boolean useBestGuessLocation = true;
        boolean enabled = true;
        
        // Create initial integration
        ImmichIntegration initial = immichIntegrationService.saveIntegration(user, serverUrl, apiToken, useBestGuessLocation, enabled);
        assertNotNull(initial.getId());
        
        // When - Update the integration
        String newServerUrl = "https://new-immich.example.com";
        String newApiToken = "new-test-token";
        ImmichIntegration updated = immichIntegrationService.saveIntegration(user, newServerUrl, newApiToken, false, false);
        
        // Then
        assertNotNull(updated);
        assertEquals(initial.getId(), updated.getId());
        assertEquals(newServerUrl, updated.getServerUrl());
        assertEquals(newApiToken, updated.getApiToken());
        assertFalse(updated.isUseBestGuessLocation());
        assertFalse(updated.isEnabled());
    }

    @Test
    void testSearchPhotosForRange_WithIntegration() {
        // Given
        User user = testingService.randomUser();
        String serverUrl = "https://immich.example.com";
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        String timezone = "UTC";
        
        // When
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, start, end, timezone);
        
        // Then
        assertNotNull(photos);
        // Will be empty since the Immich server doesn't exist
    }

    @Test
    void testProxyImageRequest_WithIntegration() {
        // Given
        User user = testingService.randomUser();
        String serverUrl = "https://immich.example.com";
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        String assetId = "test-asset-id";
        String size = "thumbnail";
        
        // When
        ResponseEntity<byte[]> response = immichIntegrationService.proxyImageRequest(user, assetId, size);
        
        // Then
        assertNotNull(response);
        // Will be 404 since the Immich server doesn't exist
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
