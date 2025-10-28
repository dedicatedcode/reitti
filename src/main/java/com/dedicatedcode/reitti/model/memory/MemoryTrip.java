package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.Trip;

import java.time.Duration;
import java.time.Instant;

public class MemoryTrip {
    private final Long id;
    private final boolean connected;
    private final MemoryVisit startVisit;
    private final MemoryVisit endVisit;

    private final Instant startTime;
    private final Instant endTime;

    public static MemoryTrip create(Trip trip) {
        return new MemoryTrip(null, true, MemoryVisit.create(trip.getStartVisit()), MemoryVisit.create(trip.getEndVisit()), trip.getStartTime(), trip.getEndTime());
    }

    public MemoryTrip(Long id, boolean connected, MemoryVisit startVisit, MemoryVisit endVisit, Instant startTime, Instant endTime) {
        this.id = id;
        this.connected = connected;
        this.startVisit = startVisit;
        this.endVisit = endVisit;
        this.startTime = startTime;
        this.endTime = endTime;
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

    public MemoryVisit getStartVisit() {
        return this.startVisit;
    }

    public MemoryVisit getEndVisit() {
        return this.endVisit;
    }
}
