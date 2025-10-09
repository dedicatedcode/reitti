package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;

public class MemoryBlockImageGallery implements MemoryBlockPart, Serializable {
    
    private final Long id;
    private final Long blockId;
    private final String imageUrl;
    private final String caption;
    private final Integer position;

    public MemoryBlockImageGallery(Long blockId, String imageUrl, String caption, Integer position) {
        this(null, blockId, imageUrl, caption, position);
    }

    public MemoryBlockImageGallery(Long id, Long blockId, String imageUrl, String caption, Integer position) {
        this.id = id;
        this.blockId = blockId;
        this.imageUrl = imageUrl;
        this.caption = caption;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Long getBlockId() {
        return blockId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCaption() {
        return caption;
    }

    public Integer getPosition() {
        return position;
    }

    @Override
    public BlockType getType() {
        return BlockType.IMAGE_GALLERY;
    }

    public MemoryBlockImageGallery withId(Long id) {
        return new MemoryBlockImageGallery(id, this.blockId, this.imageUrl, this.caption, this.position);
    }

    public MemoryBlockImageGallery withCaption(String caption) {
        return new MemoryBlockImageGallery(this.id, this.blockId, this.imageUrl, caption, this.position);
    }

    public MemoryBlockImageGallery withPosition(Integer position) {
        return new MemoryBlockImageGallery(this.id, this.blockId, this.imageUrl, this.caption, position);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockImageGallery that = (MemoryBlockImageGallery) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlockImageGallery{" +
                "id=" + id +
                ", blockId=" + blockId +
                ", imageUrl='" + imageUrl + '\'' +
                ", caption='" + caption + '\'' +
                ", position=" + position +
                '}';
    }
}
