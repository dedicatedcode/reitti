package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.*;
import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.ImmichAlbumResult;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import com.dedicatedcode.reitti.service.security.OutboundHttp;
import com.dedicatedcode.reitti.service.security.OutboundUrlValidator;
import com.dedicatedcode.reitti.service.security.UnsafeUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ImmichIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ImmichIntegrationService.class);
    private static final Pattern ASSET_ID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    // asset ids end up in request URLs and storage paths, so they must never contain path or query characters
    private static final Pattern SAFE_ASSET_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final Set<String> IMAGE_SIZES = Set.of("thumbnail", "preview", "fullsize");
    private static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;

    private final ImmichIntegrationJdbcService immichIntegrationJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final RestTemplate restTemplate;
    private final StorageService storageService;
    private final OutboundUrlValidator outboundUrlValidator;

    public ImmichIntegrationService(ImmichIntegrationJdbcService immichIntegrationJdbcService,
                                    RawLocationPointJdbcService rawLocationPointJdbcService,
                                    RestTemplate restTemplate,
                                    StorageService storageService,
                                    OutboundUrlValidator outboundUrlValidator) {
        this.immichIntegrationJdbcService = immichIntegrationJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.restTemplate = restTemplate;
        this.storageService = storageService;
        this.outboundUrlValidator = outboundUrlValidator;
    }

    /**
     * Immich asset ids are UUIDs.
     */
    public static boolean isValidAssetId(String assetId) {
        return assetId != null && ASSET_ID.matcher(assetId).matches();
    }

    private static boolean isSafeAssetId(String assetId) {
        return assetId != null && SAFE_ASSET_ID.matcher(assetId).matches();
    }
    
    public Optional<ImmichIntegration> getIntegrationForUser(User user) {
        return immichIntegrationJdbcService.findByUser(user);
    }
    
    @Transactional
    public ImmichIntegration saveIntegration(User user, String serverUrl, String apiToken, String albumId, String albumName, boolean useBestGuessLocation, boolean enabled) {
        outboundUrlValidator.validate(serverUrl);
        Optional<ImmichIntegration> existingIntegration = immichIntegrationJdbcService.findByUser(user);

        ImmichIntegration integration;
        if (existingIntegration.isPresent()) {
            integration = existingIntegration.get()
                    .withServerUrl(serverUrl)
                    .withApiToken(apiToken)
                    .withAlbum(albumId, albumName)
                    .withEnabled(enabled)
                    .withUseBestGuessLocation(useBestGuessLocation);

        } else {
            integration = new ImmichIntegration(serverUrl, apiToken, albumId, albumName, useBestGuessLocation, enabled);
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
            outboundUrlValidator.validate(validateUrl);

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
                return IntegrationTestResult.failed("StatusCode: " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            return IntegrationTestResult.failed(describeFailure(serverUrl, e));
        }
    }

    /**
     * Error messages are shown to the user, so they must not contain the remote response body or low level
     * connection details (which would turn the test endpoints into a port scanner).
     */
    private String describeFailure(String serverUrl, Exception e) {
        if (e instanceof UnsafeUrlException) {
            return e.getMessage();
        }
        if (e instanceof RestClientResponseException responseException) {
            return "StatusCode: " + responseException.getStatusCode().value();
        }
        log.debug("Request to Immich server [{}] failed", serverUrl, e);
        return "Connection failed";
    }

    public ImmichAlbumResult getAlbums(String serverUrl, String apiToken) {
        if (serverUrl == null || serverUrl.trim().isEmpty() ||
            apiToken == null || apiToken.trim().isEmpty()) {
            return ImmichAlbumResult.failed(null);
        }

        try {
            String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
            String albumsUrl = baseUrl + "api/albums";
            outboundUrlValidator.validate(albumsUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", apiToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<ImmichAlbum[]> response = restTemplate.exchange(
                albumsUrl,
                HttpMethod.GET,
                entity,
                ImmichAlbum[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return ImmichAlbumResult.ok(List.of(response.getBody()));
            }
            return ImmichAlbumResult.failed("StatusCode: " + response.getStatusCode().value());
        } catch (HttpClientErrorException.Forbidden e) {
            log.debug("Album listing not permitted for Immich server [{}]", serverUrl, e);
            return ImmichAlbumResult.permissionDenied();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.debug("Unauthorized when listing albums from Immich server [{}]", serverUrl, e);
            return ImmichAlbumResult.authFailed();
        } catch (Exception e) {
            return ImmichAlbumResult.failed(describeFailure(serverUrl, e));
        }
    }
    
    public List<PhotoResponse> searchPhotosForRange(User user, LocalDate start, LocalDate end, String timezone) {
        ZoneId userTimezone = ZoneId.of(timezone);
        Instant startOfDay = start.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = end.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
        return searchPhotosForRange(user, startOfDay, endOfDay);
    }

    public List<PhotoResponse> searchPhotosForRange(User user, Instant start, Instant end) {
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);

        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return new ArrayList<>();
        }

        ImmichIntegration integration = integrationOpt.get();

        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ?
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String searchUrl = baseUrl + "api/search/metadata";
            outboundUrlValidator.validate(searchUrl);

            ImmichSearchRequest searchRequest = new ImmichSearchRequest(DateTimeFormatter.ISO_INSTANT.format(start), DateTimeFormatter.ISO_INSTANT.format(end));
            if (integration.getAlbumId() != null && !integration.getAlbumId().isBlank()) {
                searchRequest.setAlbumIds(List.of(integration.getAlbumId()));
            }
            
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
                return convertToPhotoResponses(user, integration, response.getBody());
            }
            
        } catch (Exception e) {
            log.error("Unable to search immich data:", e);
        }
        
        return new ArrayList<>();
    }

    private List<PhotoResponse> convertToPhotoResponses(User user, ImmichIntegration integration, ImmichSearchResponse searchResponse) {
        List<PhotoResponse> photos = new ArrayList<>();
        
        if (searchResponse.getAssets() != null && searchResponse.getAssets().getItems() != null) {
            for (ImmichAsset asset : searchResponse.getAssets().getItems()) {
                String thumbnailUrl = "/api/v1/photos/immich/proxy/" + asset.getId() + "/thumbnail?userId=" + user.getId();
                String fullImageUrl = "/api/v1/photos/immich/proxy/" + asset.getId() + "/original?userId=" + user.getId();
                
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

                if (integration.isUseBestGuessLocation() && latitude == null && longitude == null) {
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
                if (latitude == null || longitude == null) {
                    continue;
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

    private record ProxiedImage(HttpStatusCode status, MediaType contentType, byte[] body) {
    }

    public ResponseEntity<byte[]> proxyImageRequest(User user, String assetId, String size) {
        if (!isSafeAssetId(assetId) || !IMAGE_SIZES.contains(size)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);

        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        ImmichIntegration integration = integrationOpt.get();

        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ?
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String imageUrl = baseUrl + "api/assets/" + assetId + "/thumbnail?size=" + size;
            outboundUrlValidator.validate(imageUrl);

            ProxiedImage image = restTemplate.execute(
                imageUrl,
                HttpMethod.GET,
                request -> request.getHeaders().add("x-api-key", integration.getApiToken()),
                response -> new ProxiedImage(response.getStatusCode(),
                                             response.getHeaders().getContentType(),
                                             OutboundHttp.readAtMost(response.getBody(), MAX_IMAGE_BYTES))
            );

            if (image != null && image.status().is2xxSuccessful() && image.body() != null) {
                HttpHeaders responseHeaders = new HttpHeaders();
                // The proxied bytes are served from our origin and possibly to other users (shared photos), so the
                // upstream content type is only passed on for images and videos.
                MediaType contentType = image.contentType() != null ? image.contentType() : MediaType.IMAGE_JPEG;
                if (isSafeMediaType(contentType)) {
                    responseHeaders.setContentType(contentType);
                } else {
                    responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    responseHeaders.setContentDisposition(ContentDisposition.attachment().build());
                }
                responseHeaders.set("X-Content-Type-Options", "nosniff");

                // Set cache headers for better performance
                responseHeaders.setCacheControl("public, max-age=3600");

                return new ResponseEntity<>(image.body(), responseHeaders, HttpStatus.OK);
            }

        } catch (Exception e) {
            // Log error but don't expose details
            log.debug("Unable to proxy Immich asset [{}]: {}", assetId, e.getMessage());
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.notFound().build();
    }

    private static boolean isSafeMediaType(MediaType mediaType) {
        String type = mediaType.getType().toLowerCase();
        String subtype = mediaType.getSubtype().toLowerCase();
        // SVG is an image type but can contain scripts
        return ("image".equals(type) && !subtype.contains("svg")) || "video".equals(type);
    }

    public String downloadImage(User user, String assetId, String targetPath) {
        ResponseEntity<byte[]> response = proxyImageRequest(user, assetId, "fullsize");
        MediaType responseType = response.getHeaders().getContentType();
        if (response.getStatusCode().is2xxSuccessful() && responseType != null && "image".equals(responseType.getType())) {
            byte[] imageData = response.getBody();
            if (imageData != null) {
                String contentType = responseType.getType() + "/" + responseType.getSubtype();
                long contentLength = imageData.length;
                String filename = assetId + getExtensionFromContentType(contentType);
                storageService.store(targetPath + "/" + filename, new java.io.ByteArrayInputStream(imageData), contentLength, contentType);
                return filename;
            }
        }
        throw new IllegalStateException("Unable to download image from Immich");
    }

    public boolean updateAssetLocation(User user, String assetId, double latitude, double longitude) {
        if (!isSafeAssetId(assetId)) {
            return false;
        }
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);

        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return false;
        }

        ImmichIntegration integration = integrationOpt.get();

        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ?
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String updateUrl = baseUrl + "api/assets";
            outboundUrlValidator.validate(updateUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", integration.getApiToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ImmichAssetUpdateRequest body = new ImmichAssetUpdateRequest(java.util.List.of(assetId), latitude, longitude);

            HttpEntity<ImmichAssetUpdateRequest> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                updateUrl,
                HttpMethod.PUT,
                entity,
                Void.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Unable to update asset location in Immich for asset [{}]", assetId, e);
            return false;
        }
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
