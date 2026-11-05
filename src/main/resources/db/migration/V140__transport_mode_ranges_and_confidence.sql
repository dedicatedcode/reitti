ALTER TABLE transport_mode_detection_configs
    ADD COLUMN min_kmh DOUBLE PRECISION,
    ADD COLUMN preference_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

WITH ordered AS (
    SELECT user_id,
           transport_mode,
           LAG(max_kmh) OVER (PARTITION BY user_id ORDER BY max_kmh NULLS LAST) AS prev_max
    FROM transport_mode_detection_configs
)
UPDATE transport_mode_detection_configs c
SET min_kmh = COALESCE(o.prev_max, 0)
FROM ordered o
WHERE c.user_id = o.user_id
  AND c.transport_mode = o.transport_mode;

UPDATE transport_mode_detection_configs SET max_kmh = 200 WHERE max_kmh IS NULL;

ALTER TABLE transport_mode_detection_configs
    DROP CONSTRAINT transport_mode_detection_configs_unique_max_kmh_per_user;

ALTER TABLE transport_mode_detection_configs
    ALTER COLUMN min_kmh SET NOT NULL,
    ALTER COLUMN max_kmh SET NOT NULL;

CREATE TABLE transport_mode_detection_settings
(
    user_id                   BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    mode_change_sensitivity   DOUBLE PRECISION NOT NULL DEFAULT 2.0
);

ALTER TABLE trip_transport_modes
    ADD COLUMN confidence DOUBLE PRECISION;

ALTER TABLE preview_trip_transport_modes
    ADD COLUMN confidence DOUBLE PRECISION;
