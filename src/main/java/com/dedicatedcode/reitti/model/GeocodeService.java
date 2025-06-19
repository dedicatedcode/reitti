package com.dedicatedcode.reitti.model;

import java.time.Instant;

public class GeocodeService {
    
    private final Long id;
    private final String name;
    private final String urlTemplate;
    private final boolean enabled;
    private final int errorCount;
    private final Instant lastUsed;
    private final Instant lastError;
    private final Long version;
    
    public GeocodeService(String name, String urlTemplate, boolean enabled, int errorCount, Instant lastUsed, Instant lastError) {
        this(null, name, urlTemplate, enabled, errorCount, lastUsed, lastError, 1L);
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

    public  GeocodeService withLastError(Instant lastError) {
        return new GeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount, this.lastUsed, lastError, this.version);
    }

    public GeocodeService withId(Long id) {
        return new GeocodeService(id, this.name, this.urlTemplate, this.enabled, this.errorCount, this.lastUsed, lastError, this.version);
    }

    public GeocodeService resetErrorCount() {
        return new GeocodeService(id, name, urlTemplate, this.enabled, 0, this.lastUsed, null, this.version);
    }
}
