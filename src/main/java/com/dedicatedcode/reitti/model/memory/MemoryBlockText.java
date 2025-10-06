package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;

public class MemoryBlockText implements Serializable {
    
    private final Long blockId;
    private final String headline;
    private final String content;

    public MemoryBlockText(Long blockId, String headline, String content) {
        this.blockId = blockId;
        this.headline = headline;
        this.content = content;
    }

    public Long getBlockId() {
        return blockId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getContent() {
        return content;
    }

    public MemoryBlockText withHeadline(String headline) {
        return new MemoryBlockText(this.blockId, headline, this.content);
    }

    public MemoryBlockText withContent(String content) {
        return new MemoryBlockText(this.blockId, this.headline, content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockText that = (MemoryBlockText) o;

        return blockId != null ? blockId.equals(that.blockId) : that.blockId == null;
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlockText{" +
                "blockId=" + blockId +
                ", headline='" + headline + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
