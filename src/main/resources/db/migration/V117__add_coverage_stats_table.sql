CREATE TABLE h3_area_coverage_stats
(
    user_id            BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id          BIGINT NULL REFERENCES devices (id) ON DELETE CASCADE,
    osm_id             BIGINT NOT NULL,
    h3_resolution      INT    NOT NULL,
    visited_cell_count BIGINT DEFAULT 0,
    total_cell_count   BIGINT NOT NULL,
    PRIMARY KEY (user_id, osm_id)
);