CREATE TABLE processed_visits
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT                   NOT NULL REFERENCES users (id),
    place_id           BIGINT                   NOT NULL REFERENCES significant_places (id),
    start_time         TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time           TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds   BIGINT                   NOT NULL,
    original_visit_ids TEXT,
    merged_count       INTEGER DEFAULT 1,

    CONSTRAINT fk_processed_visit_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_processed_visit_place FOREIGN KEY (place_id) REFERENCES significant_places (id)
);

CREATE INDEX idx_processed_visits_user ON processed_visits (user_id);
CREATE INDEX idx_processed_visits_place ON processed_visits (place_id);
CREATE INDEX idx_processed_visits_time ON processed_visits (start_time, end_time);
