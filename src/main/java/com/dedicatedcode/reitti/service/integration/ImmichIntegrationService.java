package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.ImmichAsset;
import com.dedicatedcode.reitti.dto.ImmichSearchRequest;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImmichIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ImmichIntegrationService.class);

    private final ImmichIntegrationJdbcService immichIntegrationJdbcService;
    private final RestTemplate restTemplate;
    
    public ImmichIntegrationService(ImmichIntegrationJdbcService immichIntegrationJdbcService, RestTemplate restTemplate) {
        this.immichIntegrationJdbcService = immichIntegrationJdbcService;
        this.restTemplate = restTemplate;
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
    
    public boolean testConnection(String serverUrl, String apiToken) {
        if (serverUrl == null || serverUrl.trim().isEmpty() || 
            apiToken == null || apiToken.trim().isEmpty()) {
            return false;
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
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public List<PhotoResponse> searchPhotosForDay(User user, LocalDate date, String timezone) {
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
            Instant startOfDay = date.atStartOfDay(userTimezone).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

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
                return convertToPhotoResponses(response.getBody(), baseUrl);
            }
            
        } catch (Exception e) {
            log.error("Unable to search immich data:", e);
        }
        
        return new ArrayList<>();
    }
    
    private List<PhotoResponse> convertToPhotoResponses(ImmichSearchResponse searchResponse, String baseUrl) {
        List<PhotoResponse> photos = new ArrayList<>();
        
        if (searchResponse.getAssets() != null && searchResponse.getAssets().getItems() != null) {
            for (ImmichAsset asset : searchResponse.getAssets().getItems()) {
                // Use proxy URLs instead of direct Immich URLs
                String thumbnailUrl = "/api/v1/photos/proxy/" + asset.getId() + "/thumbnail";
                String fullImageUrl = "/api/v1/photos/proxy/" + asset.getId() + "/original";
                
                Double latitude = null;
                Double longitude = null;
                String dateTime = asset.getLocalDateTime();
                
                if (asset.getExifInfo() != null) {
                    latitude = asset.getExifInfo().getLatitude();
                    longitude = asset.getExifInfo().getLongitude();
                    if (asset.getExifInfo().getDateTimeOriginal() != null) {
                        dateTime = asset.getExifInfo().getDateTimeOriginal();
                    }
                }
                
                PhotoResponse photo = new PhotoResponse(
                    asset.getId(),
                    asset.getOriginalFileName(),
                    thumbnailUrl,
                    fullImageUrl,
                    latitude,
                    longitude,
                    dateTime
                );
                
                photos.add(photo);
            }
        }
        
        return photos;
    }
}
