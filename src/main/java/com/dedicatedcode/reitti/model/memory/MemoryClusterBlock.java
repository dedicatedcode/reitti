package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class MemoryClusterBlock implements MemoryBlockPart, Serializable {

    private final Long blockId;
    private final List<Long> tripIds; // IDs of MemoryBlockTrip instances in this cluster/journey

    public MemoryClusterBlock(Long blockId, List<Long> tripIds) {
        this.blockId = blockId;
        this.tripIds = tripIds != null ? List.copyOf(tripIds) : List.of(); // Immutable copy
    }

    public Long getBlockId() {
        return blockId;
    }

    public List<Long> getTripIds() {
        return tripIds;
    }

    // Aggregation methods for combined journey data (computed from trips)
    // These assume trips are loaded separately (e.g., via a service) and passed in or cached.
    // For simplicity, they return null if no trips; in practice, a service would handle loading.

    /**
     * Gets the earliest start time from all trips in the cluster.
     * @param trips List of MemoryBlockTrip objects (loaded by IDs).
     * @return Earliest start time, or null if no trips.
     */
    public Instant getCombinedStartTime(List<MemoryBlockTrip> trips) {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(MemoryBlockTrip::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Gets the latest end time from all trips in the cluster.
     * @param trips List of MemoryBlockTrip objects (loaded by IDs).
     * @return Latest end time, or null if no trips.
     */
    public Instant getCombinedEndTime(List<MemoryBlockTrip> trips) {
        if (trips == null || trips.isEmpty()) return null;
        return trips.stream()
                .map(MemoryBlockTrip::getEndTime)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Gets the total duration in seconds from all trips in the cluster.
     * @param trips List of MemoryBlockTrip objects (loaded by IDs).
     * @return Total duration, or 0 if no trips.
     */
    public Long getCombinedDurationSeconds(List<MemoryBlockTrip> trips) {
        if (trips == null || trips.isEmpty()) return 0L;
        return trips.stream()
                .mapToLong(MemoryBlockTrip::getDurationSeconds)
                .sum();
    }

    /**
     * Gets a list of start locations (place names) from all trips in the cluster.
     * @param trips List of MemoryBlockTrip objects (loaded by IDs).
     * @return List of start place names, or empty list if no trips.
     */
    public List<String> getCombinedStartPlaces(List<MemoryBlockTrip> trips) {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(MemoryBlockTrip::getStartPlaceName)
                .toList();
    }

    /**
     * Gets a list of end locations (place names) from all trips in the cluster.
     * @param trips List of MemoryBlockTrip objects (loaded by IDs).
     * @return List of end place names, or empty list if no trips.
     */
    public List<String> getCombinedEndPlaces(List<MemoryBlockTrip> trips) {
        if (trips == null || trips.isEmpty()) return List.of();
        return trips.stream()
                .map(MemoryBlockTrip::getEndPlaceName)
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
                '}';
    }
}
