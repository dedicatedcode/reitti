CREATE TABLE h3_area_coverage_stats
(
    user_id            BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id          BIGINT NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    osm_id             BIGINT NOT NULL,
    h3_resolution      INT    NOT NULL,
    visited_cell_count BIGINT DEFAULT 0,
    total_cell_count   BIGINT NOT NULL,
    UNIQUE NULLS NOT DISTINCT (user_id, device_id, osm_id, h3_resolution)
);

CREATE TABLE h3_cells_stats
(
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    h3_index BIGINT NOT NULL,
    last_visited_at TIMESTAMP NOT NULL,
    first_visited_at TIMESTAMP NOT NULL,
    point_count BIGINT NOT NULL,
    UNIQUE NULLS NOT DISTINCT (user_id, device_id, h3_index)
);

CREATE INDEX IF NOT EXISTS idx_source_points_h3_cell
    ON raw_source_points (h3_cell)
    WHERE h3_cell IS NOT NULL;