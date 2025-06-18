package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "geocode_services")
public class GeocodeService {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;
    
    @Column(nullable = false)
    private final String name;
    
    @Column(nullable = false, length = 1000)
    private final String urlTemplate;
    
    @Column(nullable = false)
    private final boolean enabled;
    
    @Column(nullable = false)
    private final int errorCount;
    
    @Column
    private final Instant lastUsed;
    
    @Column
    private final Instant lastError;
    
    @Version
    private final Long version;
    
    public GeocodeService() {
        this(null, null, null, true, 0, null, null, null);
    }
    
    public GeocodeService(String name, String urlTemplate) {
        this(null, name, urlTemplate, true, 0, null, null, null);
    }
    
    public GeocodeService(Long id, String name, String urlTemplate, boolean enabled, int errorCount, Instant lastUsed, Instant lastError, Long version) {
        this.id = id;
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
        this.errorCount = errorCount;
        this.lastUsed = lastUsed;
        this.lastError = lastError;
        this.version = version;
    }
    
    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUrlTemplate() { return urlTemplate; }
    public boolean isEnabled() { return enabled; }
    public int getErrorCount() { return errorCount; }
    public Instant getLastUsed() { return lastUsed; }
    public Instant getLastError() { return lastError; }
    public Long getVersion() { return version; }
    
    // Wither methods
    public GeocodeService withEnabled(boolean enabled) {
        return new GeocodeService(this.id, this.name, this.urlTemplate, enabled, this.errorCount, this.lastUsed, this.lastError, this.version);
    }

    public GeocodeService withIncrementedErrorCount() {
        return new GeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount + 1, this.lastUsed, Instant.now(), this.version);
    }

    public GeocodeService withLastUsed(Instant lastUsed) {
        return new GeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount, lastUsed, this.lastError, this.version);
    }
    
}
