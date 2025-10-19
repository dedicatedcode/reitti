package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.controller.api.ImmichPhotoApiController;
import com.dedicatedcode.reitti.dto.ImmichAsset;
import com.dedicatedcode.reitti.dto.ImmichSearchRequest;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.memory.MemoryBlockImageGallery;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.S3Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImmichIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ImmichIntegrationService.class);

    private final ImmichIntegrationJdbcService immichIntegrationJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final RestTemplate restTemplate;
    private final S3Storage s3Storage;

    public ImmichIntegrationService(ImmichIntegrationJdbcService immichIntegrationJdbcService,
                                    RawLocationPointJdbcService rawLocationPointJdbcService,
                                    RestTemplate restTemplate,
                                    S3Storage s3Storage) {
        this.immichIntegrationJdbcService = immichIntegrationJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.restTemplate = restTemplate;
        this.s3Storage = s3Storage;
    }
    
    public Optional<ImmichIntegration> getIntegrationForUser(User user) {
        return immichIntegrationJdbcService.findByUser(user);
    }
    
    @Transactional
    public ImmichIntegration saveIntegration(User user, String serverUrl, String apiToken, boolean enabled) {
        Optional<ImmichIntegration> existingIntegration = immichIntegrationJdbcService.findByUser(user);
        
        ImmichIntegration integration;
        if (existingIntegration.isPresent()) {
            integration = existingIntegration.get()
                    .withServerUrl(serverUrl)
                    .withApiToken(apiToken)
                    .withEnabled(enabled);

        } else {
            integration = new ImmichIntegration(serverUrl, apiToken, enabled);
        }
        
        return immichIntegrationJdbcService.save(user, integration);
    }
    
    public IntegrationTestResult testConnection(String serverUrl, String apiToken) {
        if (serverUrl == null || serverUrl.trim().isEmpty() || 
            apiToken == null || apiToken.trim().isEmpty()) {
            return IntegrationTestResult.failed();
        }

        try {
            String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
            String validateUrl = baseUrl + "api/auth/validateToken";
            
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", apiToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                validateUrl, 
                HttpMethod.POST,
                entity, 
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return IntegrationTestResult.ok();
            } else {
                return IntegrationTestResult.failed("StatusCode: " + response.getStatusCode() + " Message:" + response.getBody());
            }
        } catch (Exception e) {
            return new IntegrationTestResult(false, e.getMessage());
        }
    }
    
    public List<PhotoResponse> searchPhotosForRange(User user, LocalDate start, LocalDate end, String timezone) {
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);
        
        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return new ArrayList<>();
        }
        
        ImmichIntegration integration = integrationOpt.get();
        
        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ? 
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String searchUrl = baseUrl + "api/search/metadata";

            ZoneId userTimezone = ZoneId.of(timezone);
            // Convert LocalDate to start and end Instant for the selected date in user's timezone
            Instant startOfDay = start.atStartOfDay(userTimezone).toInstant();
            Instant endOfDay = end.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

            ImmichSearchRequest searchRequest = new ImmichSearchRequest(DateTimeFormatter.ISO_INSTANT.format(startOfDay), DateTimeFormatter.ISO_INSTANT.format(endOfDay));
            
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", integration.getApiToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            
            HttpEntity<ImmichSearchRequest> entity = new HttpEntity<>(searchRequest, headers);
            
            ResponseEntity<ImmichSearchResponse> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.POST,
                entity,
                ImmichSearchResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return convertToPhotoResponses(user, response.getBody(), baseUrl);
            }
            
        } catch (Exception e) {
            log.error("Unable to search immich data:", e);
        }
        
        return new ArrayList<>();
    }

    private List<PhotoResponse> convertToPhotoResponses(User user, ImmichSearchResponse searchResponse, String baseUrl) {
        List<PhotoResponse> photos = new ArrayList<>();
        
        if (searchResponse.getAssets() != null && searchResponse.getAssets().getItems() != null) {
            for (ImmichAsset asset : searchResponse.getAssets().getItems()) {
                String thumbnailUrl = "/api/v1/photos/immich/proxy/" + asset.getId() + "/thumbnail";
                String fullImageUrl = "/api/v1/photos/immich/proxy/" + asset.getId() + "/original";
                
                Double latitude = null;
                Double longitude = null;
                String dateTime = asset.getLocalDateTime();
                boolean timeMatched = false;
                if (asset.getExifInfo() != null) {
                    latitude = asset.getExifInfo().getLatitude();
                    longitude = asset.getExifInfo().getLongitude();
                    if (asset.getExifInfo().getDateTimeOriginal() != null) {
                        dateTime = asset.getExifInfo().getDateTimeOriginal();
                    }

                }

                if (latitude == null && longitude == null) {
                    log.debug("Asset [{}] had no exif data, will try to match it to a point we know of.", asset.getId());
                    ZonedDateTime takenAt = ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    ZonedDateTime utc = takenAt.withZoneSameInstant(ZoneId.of("UTC"));
                    Optional<RawLocationPoint> proximatePoint = this.rawLocationPointJdbcService.findProximatePoint(user, utc.toInstant(), 60);
                    if (proximatePoint.isPresent()) {
                        latitude = proximatePoint.get().getLatitude();
                        longitude = proximatePoint.get().getLongitude();
                        timeMatched = true;
                    }
                }
                PhotoResponse photo = new PhotoResponse(
                    asset.getId(),
                    asset.getOriginalFileName(),
                    thumbnailUrl,
                    fullImageUrl,
                    latitude,
                    longitude,
                    dateTime,
                    timeMatched
                );
                
                photos.add(photo);
            }
        }
        
        return photos;
    }

    public ResponseEntity<byte[]> proxyImageRequest(User user, String assetId, String size) {
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);

        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        ImmichIntegration integration = integrationOpt.get();

        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ?
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String imageUrl = baseUrl + "api/assets/" + assetId + "/thumbnail?size=" + size;

            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", integration.getApiToken());

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                imageUrl,
                HttpMethod.GET,
                entity,
                byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                HttpHeaders responseHeaders = new HttpHeaders();

                // Copy content type from Immich response if available
                if (response.getHeaders().getContentType() != null) {
                    responseHeaders.setContentType(response.getHeaders().getContentType());
                } else {
                    // Default to JPEG for images
                    responseHeaders.setContentType(MediaType.IMAGE_JPEG);
                }

                // Set cache headers for better performance
                responseHeaders.setCacheControl("public, max-age=3600");

                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
            }

        } catch (Exception e) {
            // Log error but don't expose details
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.notFound().build();
    }

    public String downloadImage(User user, String assetId, String targetPath) {
        ResponseEntity<byte[]> response = proxyImageRequest(user, assetId, "fullsize");
        if (response.getStatusCode().is2xxSuccessful()) {
            byte[] imageData = response.getBody();
            if (imageData != null) {
                String contentType = response.getHeaders().getContentType() != null ? response.getHeaders().getContentType().toString() : "image/jpeg";
                long contentLength = imageData.length;
                String filename = UUID.randomUUID() + getExtensionFromContentType(contentType);
                s3Storage.store(targetPath +"/" + filename, new java.io.ByteArrayInputStream(imageData), contentLength, contentType);
                return filename;
            }
        }
        throw new IllegalStateException("Unable to download image from Immich");
    }


    private String getExtensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case null, default -> ".jpg"; // default

        };
    }
}
