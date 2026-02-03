package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.ImmichAsset;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;

    private static final String IMMICH_BASE_URL = "http://localhost:8089";

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

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
        String serverUrl = IMMICH_BASE_URL;
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
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Mock the validateToken endpoint
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/auth/validateToken"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess("{\"authStatus\":true}", MediaType.APPLICATION_JSON));
        
        // When
        IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);
        
        // Then
        assertNotNull(result);
        assertTrue(result.success());
        mockServer.verify();
    }

    @Test
    void testTestConnection_Failure() {
        // Given
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Mock the validateToken endpoint to return 401
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/auth/validateToken"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withUnauthorizedRequest().body("{\"message\":\"Invalid token\"}"));
        
        // When
        IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);
        
        // Then
        assertNotNull(result);
        assertFalse(result.success());
        mockServer.verify();
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
        assertTrue(photos.isEmpty());
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
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        boolean useBestGuessLocation = true;
        boolean enabled = true;
        
        // Create initial integration
        ImmichIntegration initial = immichIntegrationService.saveIntegration(user, serverUrl, apiToken, useBestGuessLocation, enabled);
        assertNotNull(initial.getId());
        
        // When - Update the integration
        String newServerUrl = "http://localhost:8090";
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
    void testSearchPhotosForRange_WithIntegration() throws Exception {
        // Given
        User user = testingService.randomUser();
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        String timezone = "UTC";
        
        // Mock the search endpoint
        ImmichSearchResponse searchResponse = new ImmichSearchResponse();
        ImmichSearchResponse.AssetsResult assetsResult = new ImmichSearchResponse.AssetsResult();
        assetsResult.setItems(List.of());
        assetsResult.setTotal(0);
        searchResponse.setAssets(assetsResult);
        
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/search/metadata"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess(objectMapper.writeValueAsString(searchResponse), MediaType.APPLICATION_JSON));
        
        // When
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, start, end, timezone);
        
        // Then
        assertNotNull(photos);
        assertTrue(photos.isEmpty());
        mockServer.verify();
    }

    @Test
    void testProxyImageRequest_WithIntegration() {
        // Given
        User user = testingService.randomUser();
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        String assetId = "test-asset-id";
        String size = "thumbnail";
        
        // Mock the image proxy endpoint
        byte[] imageData = new byte[]{1, 2, 3, 4, 5};
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/assets/" + assetId + "/thumbnail?size=" + size))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess(imageData, MediaType.IMAGE_JPEG));
        
        // When
        ResponseEntity<byte[]> response = immichIntegrationService.proxyImageRequest(user, assetId, size);
        
        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(imageData, response.getBody());
        mockServer.verify();
    }

    @Test
    void testSearchPhotosForRange_WithAssets() throws Exception {
        // Given
        User user = testingService.randomUser();
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        String timezone = "UTC";
        
        // Create mock asset with EXIF data
        ImmichAsset asset = new ImmichAsset();
        asset.setId("photo-1");
        asset.setOriginalFileName("test.jpg");
        asset.setLocalDateTime("2024-01-01T12:00:00Z");
        
        ImmichAsset.ExifInfo exifInfo = new ImmichAsset.ExifInfo();
        exifInfo.setLatitude(40.7128);
        exifInfo.setLongitude(-74.0060);
        exifInfo.setDateTimeOriginal("2024-01-01T12:00:00Z");
        asset.setExifInfo(exifInfo);
        
        // Mock the search endpoint
        ImmichSearchResponse searchResponse = new ImmichSearchResponse();
        ImmichSearchResponse.AssetsResult assetsResult = new ImmichSearchResponse.AssetsResult();
        assetsResult.setItems(List.of(asset));
        assetsResult.setTotal(1);
        searchResponse.setAssets(assetsResult);
        
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/search/metadata"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess(objectMapper.writeValueAsString(searchResponse), MediaType.APPLICATION_JSON));
        
        // When
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, start, end, timezone);
        
        // Then
        assertNotNull(photos);
        assertEquals(1, photos.size());
        PhotoResponse photo = photos.get(0);
        assertEquals("photo-1", photo.getId());
        assertEquals("test.jpg", photo.getFileName());
        assertEquals(40.7128, photo.getLatitude());
        assertEquals(-74.0060, photo.getLongitude());
        assertFalse(photo.isTimeMatched());
        mockServer.verify();
    }

    @Test
    void testSearchPhotosForRange_WithMatchingLocation() throws Exception {
        // Given
        User user = testingService.randomUser();
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        // Create a raw location point for matching
        Instant photoTime = Instant.parse("2024-01-01T12:00:00Z");
        rawLocationPointJdbcService.save(user, new RawLocationPoint(
                null,
                photoTime,
                10.0,
                100.0,
                new GeoPoint(40.7128, -74.0060),
                false,
                false,
                false,
                false
        ));
        
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        String timezone = "UTC";
        
        // Create mock asset without EXIF location
        ImmichAsset asset = new ImmichAsset();
        asset.setId("photo-1");
        asset.setOriginalFileName("test.jpg");
        asset.setLocalDateTime("2024-01-01T12:00:00Z");
        asset.setExifInfo(new ImmichAsset.ExifInfo());
        
        // Mock the search endpoint
        ImmichSearchResponse searchResponse = new ImmichSearchResponse();
        ImmichSearchResponse.AssetsResult assetsResult = new ImmichSearchResponse.AssetsResult();
        assetsResult.setItems(List.of(asset));
        assetsResult.setTotal(1);
        searchResponse.setAssets(assetsResult);
        
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/search/metadata"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess(objectMapper.writeValueAsString(searchResponse), MediaType.APPLICATION_JSON));
        
        // When
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, start, end, timezone);
        
        // Then
        assertNotNull(photos);
        assertEquals(1, photos.size());
        PhotoResponse photo = photos.get(0);
        assertEquals("photo-1", photo.getId());
        assertEquals("test.jpg", photo.getFileName());
        assertTrue(photo.isTimeMatched());
        assertEquals(40.7128, photo.getLatitude());
        assertEquals(-74.0060, photo.getLongitude());
        mockServer.verify();
    }

    @Test
    void testDownloadImage_Success() throws Exception {
        // Given
        User user = testingService.randomUser();
        String serverUrl = IMMICH_BASE_URL;
        String apiToken = "test-token";
        
        // Create integration
        immichIntegrationService.saveIntegration(user, serverUrl, apiToken, true, true);
        
        String assetId = "test-asset-id";
        String targetPath = "test-path";
        
        // Mock the image download endpoint
        byte[] imageData = new byte[]{1, 2, 3, 4, 5};
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/assets/" + assetId + "/thumbnail?size=fullsize"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", apiToken))
                .andRespond(withSuccess(imageData, MediaType.IMAGE_JPEG));
        
        // When
        String filename = immichIntegrationService.downloadImage(user, assetId, targetPath);
        
        // Then
        assertNotNull(filename);
        assertTrue(filename.endsWith(".jpg"));
        assertTrue(storageService.exists(targetPath + "/" + filename));
        mockServer.verify();
    }
}
