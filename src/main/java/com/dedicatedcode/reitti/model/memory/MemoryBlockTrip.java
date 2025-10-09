package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;

public class MemoryBlockTrip implements MemoryBlockPart, Serializable {
    
    private final Long blockId;
    private final Long tripId;

    public MemoryBlockTrip(Long blockId, Long tripId) {
        this.blockId = blockId;
        this.tripId = tripId;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getTripId() {
        return tripId;
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

        if (blockId != null ? !blockId.equals(that.blockId) : that.blockId != null) return false;
        return tripId != null ? tripId.equals(that.tripId) : that.tripId == null;
    }

    @Override
    public int hashCode() {
        int result = blockId != null ? blockId.hashCode() : 0;
        result = 31 * result + (tripId != null ? tripId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MemoryBlockTrip{" +
                "blockId=" + blockId +
                ", tripId=" + tripId +
                '}';
    }
}
