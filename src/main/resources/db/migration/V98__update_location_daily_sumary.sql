DROP MATERIALIZED VIEW IF EXISTS location_daily_sumary;

CREATE MATERIALIZED VIEW location_daily_summary AS
SELECT
    user_id,
    timestamp::date AS day,
    COUNT(*) AS point_count,
    MIN(timestamp) AS min_ts,
    MAX(timestamp) AS max_ts,
    ST_Extent(geom) AS bbox
FROM raw_location_points
GROUP BY user_id, timestamp::date;

CREATE UNIQUE INDEX ON location_daily_summary (user_id, day);
