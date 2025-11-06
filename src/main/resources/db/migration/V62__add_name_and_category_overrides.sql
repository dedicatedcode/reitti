CREATE TABLE significant_places_overrides
(
    user_id  BIGINT REFERENCES users (id),
    geom geometry(POINT, 4326),
    name     VARCHAR(255),
    category VARCHAR(255),
    timezone VARCHAR(255),
    CONSTRAINT unique_user_geom UNIQUE (user_id, geom)
);

INSERT INTO significant_places_overrides SELECT user_id, geom, name, type, timezone FROM significant_places ON CONFLICT DO NOTHING;