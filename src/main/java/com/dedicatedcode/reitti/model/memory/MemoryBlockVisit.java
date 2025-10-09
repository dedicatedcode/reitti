package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class MemoryBlockVisit implements MemoryBlockPart, Serializable {
    
    private final Long blockId;
    private final Long originalProcessedVisitId;
    private final String placeName;
    private final String placeAddress;
    private final Double latitude;
    private final Double longitude;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;

    public MemoryBlockVisit(Long blockId, Long originalProcessedVisitId, String placeName, String placeAddress, 
                           Double latitude, Double longitude, Instant startTime, Instant endTime, Long durationSeconds) {
        this.blockId = blockId;
        this.originalProcessedVisitId = originalProcessedVisitId;
        this.placeName = placeName;
        this.placeAddress = placeAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getOriginalProcessedVisitId() {
        return originalProcessedVisitId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getPlaceAddress() {
        return placeAddress;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
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

    @Override
    public BlockType getType() {
        return BlockType.VISIT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockVisit that = (MemoryBlockVisit) o;

        return Objects.equals(blockId, that.blockId);
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlockVisit{" +
                "blockId=" + blockId +
                ", originalProcessedVisitId=" + originalProcessedVisitId +
                ", placeName='" + placeName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
