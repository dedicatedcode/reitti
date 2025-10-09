package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.util.Objects;

public class MemoryBlockVisit implements MemoryBlockPart, Serializable {
    
    private final Long blockId;
    private final Long processedVisitId;

    public MemoryBlockVisit(Long blockId, Long processedVisitId) {
        this.blockId = blockId;
        this.processedVisitId = processedVisitId;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Long getProcessedVisitId() {
        return processedVisitId;
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

        if (!Objects.equals(blockId, that.blockId)) return false;
        return Objects.equals(processedVisitId, that.processedVisitId);
    }

    @Override
    public int hashCode() {
        int result = blockId != null ? blockId.hashCode() : 0;
        result = 31 * result + (processedVisitId != null ? processedVisitId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MemoryBlockVisit{" +
                "blockId=" + blockId +
                ", processedVisitId=" + processedVisitId +
                '}';
    }
}
