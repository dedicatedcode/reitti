package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.ImmichAsset;
import com.dedicatedcode.reitti.dto.ImmichSearchRequest;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImmichIntegrationService {
    
    private final ImmichIntegrationRepository immichIntegrationRepository;
    private final RestTemplate restTemplate;
    
    public ImmichIntegrationService(ImmichIntegrationRepository immichIntegrationRepository, RestTemplate restTemplate) {
        this.immichIntegrationRepository = immichIntegrationRepository;
        this.restTemplate = restTemplate;
    }
    
    public Optional<ImmichIntegration> getIntegrationForUser(User user) {
        return immichIntegrationRepository.findByUser(user);
    }
    
    @Transactional
    public ImmichIntegration saveIntegration(User user, String serverUrl, String apiToken, boolean enabled) {
        Optional<ImmichIntegration> existingIntegration = immichIntegrationRepository.findByUser(user);
        
        ImmichIntegration integration;
        if (existingIntegration.isPresent()) {
            integration = existingIntegration.get();
            integration.setServerUrl(serverUrl);
            integration.setApiToken(apiToken);
            integration.setEnabled(enabled);
        } else {
            integration = new ImmichIntegration(user, serverUrl, apiToken, enabled);
        }
        
        return immichIntegrationRepository.save(integration);
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
    
    public List<PhotoResponse> searchPhotosForDay(User user, LocalDate date) {
        Optional<ImmichIntegration> integrationOpt = getIntegrationForUser(user);
        
        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return new ArrayList<>();
        }
        
        ImmichIntegration integration = integrationOpt.get();
        
        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ? 
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String searchUrl = baseUrl + "api/search/metadata";
            
            // Create date range for the specific day
            String startOfDay = date.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String endOfDay = date.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            ImmichSearchRequest searchRequest = new ImmichSearchRequest(startOfDay, endOfDay);
            
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
            // Log error but don't throw - return empty list
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
