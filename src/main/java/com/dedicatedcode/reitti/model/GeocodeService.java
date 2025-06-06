package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "geocode_services")
public class GeocodeService {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, length = 1000)
    private String urlTemplate;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(nullable = false)
    private int errorCount = 0;
    
    @Column(nullable = false)
    private int maxErrors = 10;
    
    @Column
    private Instant lastUsed;
    
    @Column
    private Instant lastError;
    
    @Column(nullable = false)
    private boolean isDefault = false;
    
    public GeocodeService() {}
    
    public GeocodeService(String name, String urlTemplate, boolean isDefault) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.isDefault = isDefault;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    
    public int getMaxErrors() { return maxErrors; }
    public void setMaxErrors(int maxErrors) { this.maxErrors = maxErrors; }
    
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
    
    public Instant getLastError() { return lastError; }
    public void setLastError(Instant lastError) { this.lastError = lastError; }
    
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
