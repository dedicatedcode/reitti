-- Add memory_block_cluster table for grouping trips into journeys
-- Assumes PostGIS is enabled for spatial support if needed in the future
CREATE TABLE memory_block_cluster (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    trip_ids JSONB NOT NULL, -- JSON array of trip block IDs (e.g., [1, 2, 3])
    title VARCHAR(255),
    description TEXT
);

CREATE INDEX idx_memory_block_cluster_block_id ON memory_block_cluster(block_id);
