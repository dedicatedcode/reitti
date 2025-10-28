package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;

import java.time.Duration;
import java.time.Instant;

public class MemoryVisit {
    private final Long id;
    private final boolean connected;
    private final String name;
    private final Instant startTime;
    private final Instant endTime;
    private final double latitudeCentroid;
    private final double longitudeCentroid;

    public static MemoryVisit create(ProcessedVisit visit) {
        return new MemoryVisit(null, true, visit.getPlace().getName(), visit.getStartTime(), visit.getEndTime(), visit.getPlace().getLatitudeCentroid(), visit.getPlace().getLongitudeCentroid());
    }

    public MemoryVisit(Long id, boolean connected, String name, Instant startTime, Instant endTime, double latitudeCentroid, double longitudeCentroid) {
        this.id = id;
        this.connected = connected;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
    }

    public Long getId() {
        return this.id;
    }

    public boolean isConnected() {
        return this.connected;
    }

    public Instant getStartTime() {
        return this.startTime;
    }

    public Instant getEndTime() {
        return this.endTime;
    }

    public long getDurationSeconds() {
        return Duration.between(startTime, endTime).getSeconds();
    }

    public String getName() {
        return this.name;
    }

    public double getLatitudeCentroid() {
        return this.latitudeCentroid;
    }

    public double getLongitudeCentroid() {
        return this.longitudeCentroid;
    }
}
