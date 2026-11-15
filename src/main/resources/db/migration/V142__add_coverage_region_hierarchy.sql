CREATE TABLE coverage_region
(
    osm_id        BIGINT PRIMARY KEY,
    parent_osm_id BIGINT,
    kind          VARCHAR(16) NOT NULL,
    admin_level   INT         NOT NULL,
    resolution    INT         NOT NULL,
    total_cells   BIGINT      NOT NULL,
    depth         INT         NOT NULL,
    child_count   INT         NOT NULL DEFAULT 0,
    continent     VARCHAR(2),
    name_key      VARCHAR(64)
);

CREATE INDEX idx_coverage_region_parent ON coverage_region (parent_osm_id);
