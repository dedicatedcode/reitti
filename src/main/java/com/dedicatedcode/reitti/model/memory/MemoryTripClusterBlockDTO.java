package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.Trip;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class MemoryTripClusterBlockDTO implements MemoryBlockPart, Serializable {

    private final MemoryClusterBlock clusterBlock;
    private final List<Trip> trips;
    private final String rawLocationPointsUrl;
    private final LocalDateTime adjustedStartTime;
    private final LocalDateTime adjustedEndTime;
    private final Long completeDuration;
    private final Long movingDuration;

    public MemoryTripClusterBlockDTO(MemoryClusterBlock clusterBlock, List<Trip> trips, String rawLocationPointsUrl, LocalDateTime adjustedStartTime, LocalDateTime adjustedEndTime, Long completeDuration, Long movingDuration) {
        this.clusterBlock = clusterBlock;
        this.trips = trips != null ? List.copyOf(trips) : List.of();
        this.rawLocationPointsUrl = rawLocationPointsUrl;
        this.adjustedStartTime = adjustedStartTime;
        this.adjustedEndTime = adjustedEndTime;
        this.completeDuration = completeDuration;
        this.movingDuration = movingDuration;
    }

    public MemoryClusterBlock getClusterBlock() {
        return clusterBlock;
    }

    public List<Trip> getTrips() {
        return trips;
    }

    // Delegate to clusterBlock for common methods
    public Long getBlockId() {
        return clusterBlock.getBlockId();
    }

    public String getTitle() {
        return clusterBlock.getTitle();
    }

    public String getDescription() {
        return clusterBlock.getDescription();
    }

    public Long getCompleteDuration() {
        return completeDuration;
    }

    public Long getMovingDuration() {
        return movingDuration;
    }

    // Combined info methods
    public Instant getCombinedStartTime() {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(Trip::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public Instant getCombinedEndTime() {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(Trip::getEndTime)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public Long getCombinedDurationSeconds() {
        if (trips == null || trips.isEmpty()) return 0L;
        return trips.stream()
                .mapToLong(Trip::getDurationSeconds)
                .sum();
    }

    public List<String> getCombinedStartPlaces() {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(trip -> trip.getStartVisit() != null && trip.getStartVisit().getPlace() != null ? trip.getStartVisit().getPlace().getName() : null)
                .toList();
    }

    public List<String> getCombinedEndPlaces() {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(trip -> trip.getEndVisit() != null && trip.getEndVisit().getPlace() != null ? trip.getEndVisit().getPlace().getName() : null)
                .toList();
    }

    public String getRawLocationPointsUrl() {
        return rawLocationPointsUrl;
    }

    public LocalDateTime getAdjustedEndTime() {
        return adjustedEndTime;
    }

    public LocalDateTime getAdjustedStartTime() {
        return adjustedStartTime;
    }

    @Override
    public BlockType getType() {
        return BlockType.CLUSTER_TRIP;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryTripClusterBlockDTO that = (MemoryTripClusterBlockDTO) o;
        return Objects.equals(clusterBlock, that.clusterBlock);
    }

    @Override
    public int hashCode() {
        return clusterBlock != null ? clusterBlock.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryClusterBlockDTO{" +
                "clusterBlock=" + clusterBlock +
                ", trips=" + trips +
                '}';
    }
}
