package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.Trip;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class MemoryClusterBlock implements MemoryBlockPart, Serializable {

    private final Long blockId;
    private final List<Long> tripIds; // IDs of Trip instances in this cluster/journey
    private final String title;
    private final String description;

    public MemoryClusterBlock(Long blockId, List<Long> tripIds, String title, String description) {
        this.blockId = blockId;
        this.tripIds = tripIds != null ? List.copyOf(tripIds) : List.of(); // Immutable copy
        this.title = title;
        this.description = description;
    }

    public Long getBlockId() {
        return blockId;
    }

    public List<Long> getTripIds() {
        return tripIds;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    // Aggregation methods for combined journey data (computed from trips)
    // These assume trips are loaded separately (e.g., via a service) and passed in or cached.
    // For simplicity, they return null if no trips; in practice, a service would handle loading.

    /**
     * Gets the earliest start time from all trips in the cluster.
     * @param trips List of Trip objects (loaded by IDs).
     * @return Earliest start time, or null if no trips.
     */
    public Instant getCombinedStartTime(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(Trip::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Gets the latest end time from all trips in the cluster.
     * @param trips List of Trip objects (loaded by IDs).
     * @return Latest end time, or null if no trips.
     */
    public Instant getCombinedEndTime(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(Trip::getEndTime)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Gets the total duration in seconds from all trips in the cluster.
     * @param trips List of Trip objects (loaded by IDs).
     * @return Total duration, or 0 if no trips.
     */
    public Long getCombinedDurationSeconds(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) return 0L;
        return trips.stream()
                .mapToLong(Trip::getDurationSeconds)
                .sum();
    }

    /**
     * Gets a list of start locations (place names) from all trips in the cluster.
     * @param trips List of Trip objects (loaded by IDs).
     * @return List of start place names, or empty list if no trips.
     */
    public List<String> getCombinedStartPlaces(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(trip -> trip.getStartVisit() != null && trip.getStartVisit().getPlace() != null ? trip.getStartVisit().getPlace().getName() : null)
                .toList();
    }

    /**
     * Gets a list of end locations (place names) from all trips in the cluster.
     * @param trips List of Trip objects (loaded by IDs).
     * @return List of end place names, or empty list if no trips.
     */
    public List<String> getCombinedEndPlaces(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(trip -> trip.getEndVisit() != null && trip.getEndVisit().getPlace() != null ? trip.getEndVisit().getPlace().getName() : null)
                .toList();
    }

    // Additional combined info can be added here (e.g., total distance, transport modes)

    @Override
    public BlockType getType() {
        return BlockType.CLUSTER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryClusterBlock that = (MemoryClusterBlock) o;
        return Objects.equals(blockId, that.blockId);
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryClusterBlock{" +
                "blockId=" + blockId +
                ", tripIds=" + tripIds +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
