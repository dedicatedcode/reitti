package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class MemoryClusterBlock implements MemoryBlockPart, Serializable {

    private final Long blockId;
    private final List<Long> partIds;
    private final String title;
    private final String description;
    private final BlockType type;

    public MemoryClusterBlock(Long blockId, List<Long> partIds, String title, String description, BlockType type) {
        this.blockId = blockId;
        this.partIds = partIds != null ? List.copyOf(partIds) : List.of();
        this.title = title;
        this.description = description;
        this.type = type;
    }

    public Long getBlockId() {
        return blockId;
    }

    public List<Long> getPartIds() {
        return partIds;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public BlockType getType() {
        return this.type;
    }

    public MemoryClusterBlock withPartIds(List<Long> partIds) {
        return new MemoryClusterBlock(this.blockId, partIds, this.title, this.description, this.type);
    }

    public MemoryClusterBlock withTitle(String title) {
        return new MemoryClusterBlock(this.blockId, this.partIds, title, this.description, this.type);
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
                ", partIds=" + partIds +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

}
