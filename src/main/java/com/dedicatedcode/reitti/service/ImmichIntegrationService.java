package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ImmichIntegrationService {
    
    private final ImmichIntegrationRepository immichIntegrationRepository;
    
    public ImmichIntegrationService(ImmichIntegrationRepository immichIntegrationRepository) {
        this.immichIntegrationRepository = immichIntegrationRepository;
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
    
    @Transactional
    public void deleteIntegration(User user) {
        immichIntegrationRepository.findByUser(user).ifPresent(immichIntegrationRepository::delete);
    }
    
    public boolean testConnection(String serverUrl, String apiToken) {
        // TODO: Implement actual connection test to Immich API
        // For now, just validate that URL and token are not empty
        return serverUrl != null && !serverUrl.trim().isEmpty() && 
               apiToken != null && !apiToken.trim().isEmpty();
    }
}
