package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;

import java.time.Instant;
import java.util.Map;

public class GeocodeService {
    private final Long id;
    private final String name;
    private final String url;
    private final boolean enabled;
    private final int errorCount;
    private final Instant lastUsed;
    private final Instant lastError;
    private final GeocoderType type;
    private final Map<String, String> additionalParameters;
    private final int priority;
    private final Long version;

    public GeocodeService(String name, String url, boolean enabled, int errorCount, Instant lastUsed, Instant lastError, GeocoderType type, int priority, Map<String, String> additionalParameters) {
        this(null, name, url, enabled, errorCount, lastUsed, lastError, type, additionalParameters, priority, 1L);
    }

    public GeocodeService(Long id, String name, String url, boolean enabled, int errorCount, Instant lastUsed, Instant lastError, GeocoderType type, Map<String, String> additionalParameters, int priority, Long version) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.enabled = enabled;
        this.errorCount = errorCount;
        this.lastUsed = lastUsed;
        this.lastError = lastError;
        this.type = type;
        this.additionalParameters = additionalParameters;
        this.priority = priority;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public Instant getLastError() {
        return lastError;
    }

    public Long getVersion() {
        return version;
    }

    public GeocoderType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public String getUrlTemplate() {
        return switch (type) {
            case PHOTON -> this.url + "/reverse?lon={lng}&lat={lat}&limit=10&layer=house&layer=locality&radius=0.03";
            case PAIKKA -> {
                String urlTemplate = this.url + "/api/v1/reverse?lat={lat}&lon={lng}";
                if (this.additionalParameters.containsKey("language")) {
                    urlTemplate += "&lang=" + this.additionalParameters.get("language");
                }
                if (this.additionalParameters.containsKey("limit")) {
                    urlTemplate += "&limit=" + this.additionalParameters.get("limit");
                }
                yield urlTemplate;
            }
            case GEO_APIFY -> {
                String urlTemplate = this.url + "/v1/geocode/reverse?lat={lat}&lon={lng}&apiKey=" + this.additionalParameters.get("apiKey");
                if (this.additionalParameters.containsKey("language")) {
                    urlTemplate += "&lang=" + this.additionalParameters.get("language");
                }
                yield urlTemplate;
            }
            case NOMINATIM -> this.url + "/reverse?format=geocodejson&lat={lat}&lon={lng}";
            case GEOCODE_JSON -> url;
        };
    }

    // Wither methods
    public GeocodeService withEnabled(boolean enabled) {
        return new GeocodeService(this.id, this.name, this.url, enabled, this.errorCount, this.lastUsed, this.lastError, this.type, this.additionalParameters, this.priority, this.version);
    }

    public GeocodeService withIncrementedErrorCount() {
        return new GeocodeService(this.id, this.name, this.url, this.enabled, this.errorCount + 1, this.lastUsed, Instant.now(), this.type, this.additionalParameters, this.priority, this.version);
    }

    public GeocodeService withLastUsed(Instant lastUsed) {
        return new GeocodeService(this.id, this.name, this.url, this.enabled, this.errorCount, lastUsed, this.lastError, this.type, this.additionalParameters, this.priority, this.version);
    }

    public GeocodeService withLastError(Instant lastError) {
        return new GeocodeService(this.id, this.name, this.url, this.enabled, this.errorCount, this.lastUsed, lastError, this.type, this.additionalParameters, this.priority, this.version);
    }

    public GeocodeService withId(Long id) {
        return new GeocodeService(id, this.name, this.url, this.enabled, this.errorCount, this.lastUsed, lastError, this.type, this.additionalParameters, this.priority, this.version);
    }

    public GeocodeService resetErrorCount() {
        return new GeocodeService(id, name, this.url, this.enabled, 0, this.lastUsed, null, this.type, this.additionalParameters, this.priority, this.version);
    }

}
