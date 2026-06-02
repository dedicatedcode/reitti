INSERT INTO api_tokens (token, user_id, name, created_at, version, device_id)
SELECT
    gen_random_uuid()::text AS token,
    d.user_id,
    'Auto-generated token for device ' || d.name AS name,
    now() AS created_at,
    0 AS version,
    d.id AS device_id
FROM devices d
WHERE NOT EXISTS (
    SELECT 1
    FROM api_tokens at
    WHERE at.device_id = d.id
);