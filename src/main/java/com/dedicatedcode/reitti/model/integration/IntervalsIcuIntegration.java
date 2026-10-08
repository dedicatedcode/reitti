package com.dedicatedcode.reitti.model.integration;

import java.time.Instant;

public class IntervalsIcuIntegration {

    private final Long id;
    private final String apiKey;
    private final Long reittiDeviceId;
    private final String athleteId;
    private final String athleteName;
    private final boolean enabled;
    private final Instant lastSuccessfulFetch;
    private final Long version;

    public IntervalsIcuIntegration(String apiKey, Long reittiDeviceId, boolean enabled) {
        this(null, apiKey, reittiDeviceId, null, null, enabled, null, null);
    }

    public IntervalsIcuIntegration(Long id, String apiKey, Long reittiDeviceId, String athleteId,
                                   String athleteName, boolean enabled, Instant lastSuccessfulFetch, Long version) {
        this.id = id;
        this.apiKey = apiKey;
        this.reittiDeviceId = reittiDeviceId;
        this.athleteId = athleteId;
        this.athleteName = athleteName;
        this.enabled = enabled;
        this.lastSuccessfulFetch = lastSuccessfulFetch;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Long getReittiDeviceId() {
        return reittiDeviceId;
    }

    public String getAthleteId() {
        return athleteId;
    }

    public String getAthleteName() {
        return athleteName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastSuccessfulFetch() {
        return lastSuccessfulFetch;
    }

    public Long getVersion() {
        return version;
    }

    public IntervalsIcuIntegration withEnabled(boolean enabled) {
        return new IntervalsIcuIntegration(this.id, this.apiKey, this.reittiDeviceId, this.athleteId,
                this.athleteName, enabled, this.lastSuccessfulFetch, this.version);
    }

    public IntervalsIcuIntegration withId(Long id) {
        return new IntervalsIcuIntegration(id, this.apiKey, this.reittiDeviceId, this.athleteId,
                this.athleteName, this.enabled, this.lastSuccessfulFetch, this.version);
    }

    public IntervalsIcuIntegration withVersion(Long version) {
        return new IntervalsIcuIntegration(this.id, this.apiKey, this.reittiDeviceId, this.athleteId,
                this.athleteName, this.enabled, this.lastSuccessfulFetch, version);
    }

    public IntervalsIcuIntegration withLastSuccessfulFetch(Instant lastSuccessfulFetch) {
        return new IntervalsIcuIntegration(this.id, this.apiKey, this.reittiDeviceId, this.athleteId,
                this.athleteName, this.enabled, lastSuccessfulFetch, this.version);
    }

    public IntervalsIcuIntegration withAthlete(String athleteId, String athleteName) {
        return new IntervalsIcuIntegration(this.id, this.apiKey, this.reittiDeviceId, athleteId,
                athleteName, this.enabled, this.lastSuccessfulFetch, this.version);
    }
}
