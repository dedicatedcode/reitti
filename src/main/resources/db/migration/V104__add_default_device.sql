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