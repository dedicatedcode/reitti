package com.dedicatedcode.reitti.model.geocoding;

import com.dedicatedcode.reitti.service.geocoding.GeocodeService;

import java.time.Instant;

public class RemoteGeocodeService implements GeocodeService {
    
    private final Long id;
    private final String name;
    private final String urlTemplate;
    private final boolean enabled;
    private final int errorCount;
    private final Instant lastUsed;
    private final Instant lastError;
    private final Long version;
    
    public RemoteGeocodeService(String name, String urlTemplate, boolean enabled, int errorCount, Instant lastUsed, Instant lastError) {
        this(null, name, urlTemplate, enabled, errorCount, lastUsed, lastError, 1L);
    }
    public RemoteGeocodeService(Long id, String name, String urlTemplate, boolean enabled, int errorCount, Instant lastUsed, Instant lastError, Long version) {
        this.id = id;
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
        this.errorCount = errorCount;
        this.lastUsed = lastUsed;
        this.lastError = lastError;
        this.version = version;
    }
    
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUrlTemplate() { return urlTemplate; }
    public boolean isEnabled() { return enabled; }
    public int getErrorCount() { return errorCount; }
    public Instant getLastUsed() { return lastUsed; }
    public Instant getLastError() { return lastError; }
    public Long getVersion() { return version; }

    // Wither methods
    public RemoteGeocodeService withEnabled(boolean enabled) {
        return new RemoteGeocodeService(this.id, this.name, this.urlTemplate, enabled, this.errorCount, this.lastUsed, this.lastError, this.version);
    }

    public RemoteGeocodeService withIncrementedErrorCount() {
        return new RemoteGeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount + 1, this.lastUsed, Instant.now(), this.version);
    }

    public RemoteGeocodeService withLastUsed(Instant lastUsed) {
        return new RemoteGeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount, lastUsed, this.lastError, this.version);
    }

    public RemoteGeocodeService withLastError(Instant lastError) {
        return new RemoteGeocodeService(this.id, this.name, this.urlTemplate, this.enabled, this.errorCount, this.lastUsed, lastError, this.version);
    }

    public RemoteGeocodeService withId(Long id) {
        return new RemoteGeocodeService(id, this.name, this.urlTemplate, this.enabled, this.errorCount, this.lastUsed, lastError, this.version);
    }

    public RemoteGeocodeService resetErrorCount() {
        return new RemoteGeocodeService(id, name, urlTemplate, this.enabled, 0, this.lastUsed, null, this.version);
    }
}
