package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.Trip;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class MemoryClusterBlockDTO implements MemoryBlockPart, Serializable {

    private final MemoryClusterBlock clusterBlock;
    private final List<Trip> trips;
    private final String rawLocationPointsUrl;
    private final LocalDateTime adjustedStartTime;
    private final LocalDateTime adjustedEndTime;
    private final Long completeDuration;
    private final Long movingDuration;

    public MemoryClusterBlockDTO(MemoryClusterBlock clusterBlock, List<Trip> trips, String rawLocationPointsUrl, LocalDateTime adjustedStartTime, LocalDateTime adjustedEndTime, Long completeDuration, Long movingDuration) {
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
        return clusterBlock.getCombinedStartTime(trips);
    }

    public Instant getCombinedEndTime() {
        return clusterBlock.getCombinedEndTime(trips);
    }

    public Long getCombinedDurationSeconds() {
        return clusterBlock.getCombinedDurationSeconds(trips);
    }

    public List<String> getCombinedStartPlaces() {
        return clusterBlock.getCombinedStartPlaces(trips);
    }

    public List<String> getCombinedEndPlaces() {
        return clusterBlock.getCombinedEndPlaces(trips);
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
        return BlockType.CLUSTER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryClusterBlockDTO that = (MemoryClusterBlockDTO) o;
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
