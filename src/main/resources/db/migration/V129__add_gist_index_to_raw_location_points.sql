CREATE INDEX idx_raw_location_points_geom ON raw_location_points USING GIST (geom);
