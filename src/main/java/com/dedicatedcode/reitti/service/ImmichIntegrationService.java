package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
            // Ensure serverUrl ends with a slash for proper URL construction
            String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
            String validateUrl = baseUrl + "api/auth/validateToken";
            
            // Set up headers with bearer token
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Make the request
            ResponseEntity<String> response = restTemplate.exchange(
                validateUrl, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            // Consider 2xx status codes as successful
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            // Log the exception if needed, but return false for any connection issues
            return false;
        }
    }
}
