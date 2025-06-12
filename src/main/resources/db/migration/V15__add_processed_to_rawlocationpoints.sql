ALTER TABLE raw_location_points ADD COLUMN processed BOOLEAN DEFAULT false;

CREATE INDEX raw_location_points_processed
    on raw_location_points (processed);