ALTER TABLE user_settings
    ADD COLUMN latest_data TIMESTAMP NULL;

UPDATE user_settings us
SET latest_data = q.timestamp
FROM (SELECT user_id, max(timestamp) AS timestamp FROM raw_location_points GROUP BY user_id) AS q
WHERE q.user_id = us.user_id;