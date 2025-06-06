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
    
    @Column
    private Instant lastUsed;
    
    @Column
    private Instant lastError;
    
    
    public GeocodeService() {}
    
    public GeocodeService(String name, String urlTemplate) {
        this.name = name;
        this.urlTemplate = urlTemplate;
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
    
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
    
    public Instant getLastError() { return lastError; }
    public void setLastError(Instant lastError) { this.lastError = lastError; }
    
}
