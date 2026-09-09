CREATE TABLE no_visit_zones
(
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR     NOT NULL,
    geom       GEOMETRY(POLYGON, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_no_visit_zones_user_id ON no_visit_zones (user_id);
CREATE INDEX idx_no_visit_zones_geom ON no_visit_zones USING GIST (geom);
