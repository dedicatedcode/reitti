ALTER TABLE raw_source_points ADD COLUMN h3_res10 BIGINT NULL;
ALTER TABLE raw_location_points ADD COLUMN h3_res10 BIGINT NULL;

CREATE INDEX idx_points_h3_time
    ON raw_location_points (h3_res10, timestamp)
    WHERE h3_res10 IS NOT NULL;