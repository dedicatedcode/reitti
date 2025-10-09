package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.time.Instant;

public class MemoryBlockTrip implements MemoryBlockPart, Serializable {
    
    private final Long blockId;
    private final Long originalTripId;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Double estimatedDistanceMeters;
    private final Double travelledDistanceMeters;
    private final String transportModeInferred;
    private final String startPlaceName;
    private final Double startLatitude;
    private final Double startLongitude;
    private final String endPlaceName;
    private final Double endLatitude;
    private final Double endLongitude;

    public MemoryBlockTrip(Long blockId, Long originalTripId, Instant startTime, Instant endTime, 
                          Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters,
                          String transportModeInferred, String startPlaceName, Double startLatitude, 
                          Double startLongitude, String endPlaceName, Double endLatitude, Double endLongitude) {
        this.blockId = blockId;
        this.originalTripId = originalTripId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.travelledDistanceMeters = travelledDistanceMeters;
        this.transportModeInferred = transportModeInferred;
        this.startPlaceName = startPlaceName;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.endPlaceName = endPlaceName;
        this.endLatitude = endLatitude;
        this.endLongitude = endLongitude;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getOriginalTripId() {
        return originalTripId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public Double getEstimatedDistanceMeters() {
        return estimatedDistanceMeters;
    }

    public Double getTravelledDistanceMeters() {
        return travelledDistanceMeters;
    }

    public String getTransportModeInferred() {
        return transportModeInferred;
    }

    public String getStartPlaceName() {
        return startPlaceName;
    }

    public Double getStartLatitude() {
        return startLatitude;
    }

    public Double getStartLongitude() {
        return startLongitude;
    }

    public String getEndPlaceName() {
        return endPlaceName;
    }

    public Double getEndLatitude() {
        return endLatitude;
    }

    public Double getEndLongitude() {
        return endLongitude;
    }

    @Override
    public BlockType getType() {
        return BlockType.TRIP;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockTrip that = (MemoryBlockTrip) o;

        return blockId != null ? blockId.equals(that.blockId) : that.blockId == null;
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlockTrip{" +
                "blockId=" + blockId +
                ", originalTripId=" + originalTripId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}
