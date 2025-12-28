ALTER TABLE significant_places_overrides ADD COLUMN polygon GEOMETRY(POLYGON, 4326) DEFAULT NULL;

CREATE INDEX idx_significant_places_override_polygon ON significant_places_overrides USING GIST (polygon);

