ALTER TABLE memory_block_cluster RENAME COLUMN trip_ids TO part_ids;
ALTER TABLE memory_block_cluster ADD COLUMN type VARCHAR(255) DEFAULT 'CLUSTER_TRIP';
ALTER TABLE memory_block_cluster ALTER COLUMN type DROP DEFAULT;