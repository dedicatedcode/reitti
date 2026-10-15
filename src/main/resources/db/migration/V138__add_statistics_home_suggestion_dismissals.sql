CREATE TABLE IF NOT EXISTS statistics_home_suggestion_dismissals
(
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id     BIGINT      NOT NULL REFERENCES significant_places (id) ON DELETE CASCADE,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, place_id)
);

CREATE INDEX IF NOT EXISTS idx_h3_cells_stats_user_first_visited ON h3_cells_stats (user_id, first_visited_at);
