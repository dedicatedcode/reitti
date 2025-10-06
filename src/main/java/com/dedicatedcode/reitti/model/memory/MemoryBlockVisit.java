package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;

public class MemoryBlockVisit implements Serializable {
    
    private final Long blockId;
    private final Long visitId;

    public MemoryBlockVisit(Long blockId, Long visitId) {
        this.blockId = blockId;
        this.visitId = visitId;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getVisitId() {
        return visitId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockVisit that = (MemoryBlockVisit) o;

        if (blockId != null ? !blockId.equals(that.blockId) : that.blockId != null) return false;
        return visitId != null ? visitId.equals(that.visitId) : that.visitId == null;
    }

    @Override
    public int hashCode() {
        int result = blockId != null ? blockId.hashCode() : 0;
        result = 31 * result + (visitId != null ? visitId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MemoryBlockVisit{" +
                "blockId=" + blockId +
                ", visitId=" + visitId +
                '}';
    }
}
