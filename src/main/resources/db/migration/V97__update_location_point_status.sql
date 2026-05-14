ALTER TABLE raw_source_points ADD COLUMN status INT DEFAULT 0;
UPDATE raw_source_points SET status = 1 WHERE ignored;

CREATE OR REPLACE VIEW v_source_stream AS
WITH current_overrides AS (
    SELECT user_id, device_id, start_time, end_time
    FROM timeline_overrides
    WHERE active = true
)
-- PART A: Points from specific overridden devices
SELECT
    rsp.id AS source_point_id,
    rsp.accuracy_meters,
    rsp.timestamp,
    rsp.user_id,
    rsp.geom,
    rsp.elevation_meters,
    rsp.device_id,
    rsp.status -- Pass this through
FROM raw_source_points rsp
         JOIN current_overrides ov ON rsp.user_id = ov.user_id
    AND rsp.device_id = ov.device_id
    AND rsp.timestamp >= ov.start_time
    AND rsp.timestamp < ov.end_time
WHERE rsp.status = 0 AND rsp.invalid IS FALSE

UNION ALL

-- PART B: Points from the "Main" device
SELECT
    rsp.id AS source_point_id,
    rsp.accuracy_meters,
    rsp.timestamp,
    rsp.user_id,
    rsp.geom,
    rsp.elevation_meters,
    rsp.device_id,
    rsp.status
FROM raw_source_points rsp
WHERE rsp.device_id IS NULL
  AND rsp.status = 0
  AND rsp.invalid IS FALSE
  AND NOT EXISTS (
    SELECT 1
    FROM current_overrides ov
    WHERE ov.user_id = rsp.user_id
      AND rsp.timestamp >= ov.start_time
      AND rsp.timestamp < ov.end_time
);

ALTER TABLE raw_source_points DROP COLUMN ignored;
ALTER TABLE raw_location_points DROP COLUMN ignored;
ALTER TABLE raw_location_points DROP COLUMN invalid;

ALTER TABLE preview_raw_location_points DROP COLUMN ignored;