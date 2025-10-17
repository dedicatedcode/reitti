package com.dedicatedcode.reitti.model.memory;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class MemoryBlockImageGallery implements MemoryBlockPart, Serializable {
    
    private final Long blockId;
    private final List<GalleryImage> images;

    public MemoryBlockImageGallery(Long blockId, List<GalleryImage> images) {
        this.blockId = blockId;
        this.images = images != null ? List.copyOf(images) : List.of();
    }

    public Long getBlockId() {
        return blockId;
    }

    public List<GalleryImage> getImages() {
        return images;
    }

    @Override
    public BlockType getType() {
        return BlockType.IMAGE_GALLERY;
    }

    public MemoryBlockImageGallery withImages(List<GalleryImage> images) {
        return new MemoryBlockImageGallery(this.blockId, images);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MemoryBlockImageGallery that = (MemoryBlockImageGallery) o;

        return Objects.equals(blockId, that.blockId);
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryBlockImageGallery{" +
                "blockId=" + blockId +
                ", images=" + images +
                '}';
    }

    public static class GalleryImage implements Serializable {
        private final String imageUrl;
        private final String caption;

        public GalleryImage(String imageUrl, String caption) {
            this.imageUrl = imageUrl;
            this.caption = caption;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getCaption() {
            return caption;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GalleryImage that = (GalleryImage) o;
            return Objects.equals(imageUrl, that.imageUrl) && Objects.equals(caption, that.caption);
        }

        @Override
        public int hashCode() {
            return Objects.hash(imageUrl, caption);
        }

        @Override
        public String toString() {
            return "GalleryImage{" +
                    "imageUrl='" + imageUrl + '\'' +
                    ", caption='" + caption + '\'' +
                    '}';
        }
    }
}
