CREATE TABLE suppressed_visits
(
    id                 BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id            BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id           BIGINT REFERENCES significant_places (id) ON DELETE SET NULL,
    latitude_centroid  DOUBLE PRECISION NOT NULL,
    longitude_centroid DOUBLE PRECISION NOT NULL,
    start_time         TIMESTAMPTZ      NOT NULL,
    end_time           TIMESTAMPTZ      NOT NULL,
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_suppressed_visits_user_time ON suppressed_visits (user_id, start_time, end_time);
