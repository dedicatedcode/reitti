ALTER TABLE significant_places ADD COLUMN polygon GEOMETRY(POLYGON, 4326);
ALTER TABLE preview_significant_places ADD COLUMN polygon GEOMETRY(POLYGON, 4326);

CREATE INDEX idx_significant_places_polygon ON significant_places USING GIST (polygon);
CREATE INDEX idx_preview_significant_places_polygon ON preview_significant_places USING GIST (polygon);

