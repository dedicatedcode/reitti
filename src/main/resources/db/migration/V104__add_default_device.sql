-- Step 1: Add the boolean column (original)
ALTER TABLE devices ADD COLUMN default_device BOOLEAN DEFAULT FALSE;

-- Step 2: Create a default device for every user that lacks one
INSERT INTO devices (user_id, name, default_device,color, show_on_map, enabled, version)
SELECT id, 'Default', TRUE, '#f1ba63', TRUE, TRUE, 1
FROM users
WHERE NOT EXISTS (
    SELECT 1 FROM devices d WHERE d.user_id = users.id AND d.default_device = TRUE
);
-- Step 3: Assign the default device to all raw_source_points with no device_id
UPDATE raw_source_points rsp
SET device_id = d.id
FROM devices d
WHERE d.user_id = rsp.user_id
  AND d.default_device = TRUE
  AND rsp.device_id IS NULL;

-- Step 4: Attach every api_token without a device to the user's default device
UPDATE api_tokens at
SET device_id = d.id
FROM devices d
WHERE d.user_id = at.user_id
  AND d.default_device = TRUE
  AND at.device_id IS NULL;

-- Step 5: update existing staging data
UPDATE staging_location_points SET device_id = d.id FROM devices d WHERE d.user_id = staging_location_points.user_id AND d.default_device = TRUE AND device_id IS NULL;
ALTER TABLE staging_location_points ALTER COLUMN device_id SET NOT NULL;

-- Step 6: adjust raw_source_points
DROP VIEW IF EXISTS v_source_stream;

CREATE OR REPLACE VIEW v_source_stream AS
WITH current_overrides AS (
    SELECT user_id, device_id, start_time, end_time
    FROM timeline_overrides
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
    rsp.status
FROM raw_source_points rsp
         JOIN current_overrides ov ON rsp.user_id = ov.user_id
    AND rsp.device_id = ov.device_id
    AND rsp.timestamp >= ov.start_time
    AND rsp.timestamp < ov.end_time
WHERE rsp.status = 0 AND rsp.invalid IS FALSE

UNION ALL

-- Step B: Points from the user's "Main" (default) device where no override exists
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
         JOIN devices d ON d.id = rsp.device_id
    AND d.user_id = rsp.user_id
    AND d.default_device = TRUE
WHERE rsp.status = 0
  AND rsp.invalid IS FALSE
  AND NOT EXISTS (
    SELECT 1
    FROM current_overrides ov
    WHERE ov.user_id = rsp.user_id
      AND rsp.timestamp >= ov.start_time
      AND rsp.timestamp < ov.end_time
);

-- Step 9: add device to mqtt integrations
ALTER TABLE mqtt_integrations ADD COLUMN device_id BIGINT;
UPDATE mqtt_integrations SET device_id = d.id FROM devices d WHERE d.user_id = mqtt_integrations.user_id AND d.default_device = TRUE AND device_id IS NULL;
ALTER TABLE mqtt_integrations ALTER COLUMN device_id SET NOT NULL;
ALTER TABLE mqtt_integrations ADD CONSTRAINT fk_device_id FOREIGN KEY (device_id) REFERENCES devices(id);

-- Step 10: add device to owntracks recorder integrations
ALTER TABLE owntracks_recorder_integration ADD COLUMN reitti_device_id BIGINT;
UPDATE owntracks_recorder_integration SET reitti_device_id = d.id FROM devices d WHERE d.user_id = owntracks_recorder_integration.user_id AND d.default_device = TRUE AND reitti_device_id IS NULL;
ALTER TABLE owntracks_recorder_integration ALTER COLUMN reitti_device_id SET NOT NULL;
ALTER TABLE owntracks_recorder_integration ADD CONSTRAINT fk_device_id FOREIGN KEY (reitti_device_id) REFERENCES devices(id);