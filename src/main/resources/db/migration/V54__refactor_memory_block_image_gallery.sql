-- Refactor memory_block_image_gallery to store multiple images per block
-- Drop the old table and create a new one with JSONB for images

DROP TABLE IF EXISTS memory_block_image_gallery;

CREATE TABLE memory_block_image_gallery (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    images JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_memory_block_image_gallery_block_id ON memory_block_image_gallery(block_id);

COMMENT ON TABLE memory_block_image_gallery IS 'Stores image galleries for memory blocks with multiple images per gallery';
COMMENT ON COLUMN memory_block_image_gallery.images IS 'JSON array of image objects with imageUrl and caption fields';
