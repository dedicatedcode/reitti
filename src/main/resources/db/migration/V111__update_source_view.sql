DROP VIEW IF EXISTS v_source_stream;

CREATE OR REPLACE VIEW v_source_stream AS
WITH current_overrides AS (SELECT user_id, device_id, start_time, end_time
                           FROM timeline_overrides)
-- PART A: Points from specific overridden devices
SELECT rsp.id AS source_point_id,
       rsp.accuracy_meters,
       rsp.timestamp,
       rsp.user_id,
       rsp.geom,
       rsp.elevation_meters,
       rsp.device_id,
       rsp.status
FROM raw_source_points rsp
         JOIN current_overrides ov ON rsp.user_id = ov.user_id
    AND rsp.device_id = ov.device_id
    AND rsp.timestamp >= ov.start_time
    AND rsp.timestamp < ov.end_time
WHERE rsp.status != 1
  AND rsp.invalid IS FALSE

UNION ALL

-- Step B: Points from the user's "Main" (default) device where no override exists
SELECT rsp.id AS source_point_id,
       rsp.accuracy_meters,
       rsp.timestamp,
       rsp.user_id,
       rsp.geom,
       rsp.elevation_meters,
       rsp.device_id,
       rsp.status
FROM raw_source_points rsp
         JOIN devices d ON d.id = rsp.device_id
    AND d.user_id = rsp.user_id
    AND d.default_device = TRUE
WHERE rsp.status != 1
  AND rsp.invalid IS FALSE
  AND NOT EXISTS (SELECT 1
                  FROM current_overrides ov
                  WHERE ov.user_id = rsp.user_id
                    AND rsp.timestamp >= ov.start_time
                    AND rsp.timestamp < ov.end_time);


ALTER TABLE raw_location_points
    ADD COLUMN status INT DEFAULT 0;

-- copy old ignored points into raw_location_points
INSERT INTO raw_location_points(user_id, accuracy_meters, timestamp, geom, elevation_meters, source_point_id, processed, synthetic, status)
SELECT user_id,
       accuracy_meters,
       timestamp,
       geom,
       elevation_meters,
       source_point_id,
       FALSE,
       FALSE,
       status
FROM v_source_stream
WHERE status = 2 ON CONFLICT (user_id, timestamp) DO UPDATE SET status = 2;

