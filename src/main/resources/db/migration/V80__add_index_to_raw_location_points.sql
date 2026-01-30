CREATE INDEX idx_covering_user_time
    ON raw_location_points (user_id, timestamp)
    INCLUDE (geom);