ALTER TABLE raw_source_points ADD COLUMN h3_cell BIGINT NULL;
ALTER TABLE raw_location_points ADD COLUMN h3_cell BIGINT NULL;

CREATE INDEX idx_points_h3_time
    ON raw_location_points (h3_cell, timestamp)
    WHERE h3_cell IS NOT NULL;