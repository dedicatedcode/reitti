package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
}
