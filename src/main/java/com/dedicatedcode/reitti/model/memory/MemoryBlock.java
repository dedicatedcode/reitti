package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;

public class MemoryBlock implements Serializable {
    
    private final Long id;
    private final Long memoryId;
    private final BlockType blockType;
    private final Integer position;
    private final Long version;

    public MemoryBlock(Long memoryId, BlockType blockType, Integer position) {
        this(null, memoryId, blockType, position, 1L);
    }

    public MemoryBlock(Long id, Long memoryId, BlockType blockType, Integer position, Long version) {
        this.id = id;
        this.memoryId = memoryId;
        this.blockType = blockType;
        this.position = position;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public Integer getPosition() {
        return position;
    }

    public Long getVersion() {
        return version;
    }

    public MemoryBlock withId(Long id) {
        return new MemoryBlock(id, this.memoryId, this.blockType, this.position, this.version);
    }

    public MemoryBlock withPosition(Integer position) {
        return new MemoryBlock(this.id, this.memoryId, this.blockType, position, this.version);
    }

    public MemoryBlock withVersion(Long version) {
        return new MemoryBlock(this.id, this.memoryId, this.blockType, this.position, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlock that = (MemoryBlock) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlock{" +
                "id=" + id +
                ", memoryId=" + memoryId +
                ", blockType=" + blockType +
                ", position=" + position +
                ", version=" + version +
                '}';
    }
}
