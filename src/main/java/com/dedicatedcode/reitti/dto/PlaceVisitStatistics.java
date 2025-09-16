package com.dedicatedcode.reitti.dto;

import java.time.Instant;

public class PlaceVisitStatistics {
    private final long totalVisits;
    private final Instant firstVisit;
    private final Instant lastVisit;

    public PlaceVisitStatistics(long totalVisits, Instant firstVisit, Instant lastVisit) {
        this.totalVisits = totalVisits;
        this.firstVisit = firstVisit;
        this.lastVisit = lastVisit;
    }

    public long getTotalVisits() {
        return totalVisits;
    }

    public Instant getFirstVisit() {
        return firstVisit;
    }

    public Instant getLastVisit() {
        return lastVisit;
    }
}
