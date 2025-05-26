ALTER TABLE significant_places
    ADD COLUMN geom geometry;
ALTER TABLE significant_places
    ADD COLUMN total_duration_seconds BIGINT;
ALTER TABLE significant_places
    ADD COLUMN visit_count INTEGER;

