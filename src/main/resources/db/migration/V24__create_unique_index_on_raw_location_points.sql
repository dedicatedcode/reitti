DELETE FROM raw_location_points a
    USING raw_location_points b
WHERE a.id < b.id
  AND a.user_id = b.user_id
  AND a.timestamp = b.timestamp;

DROP INDEX raw_location_points_user_id_timestamp_index;

CREATE UNIQUE INDEX raw_location_points_user_id_timestamp_uindex
    ON raw_location_points (user_id, timestamp);
