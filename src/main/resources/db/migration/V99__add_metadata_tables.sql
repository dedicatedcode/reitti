CREATE TABLE location_metadata
(
    id           SERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users (id) NOT NULL,
    -- Acts purely as a hint/context for the UI or recalculation filter
    context_type VARCHAR(10) NOT NULL, -- 'TRIP' or 'VISIT'
    -- The absolute temporal anchor
    time_range   TSTZRANGE   NOT NULL,
    -- The user data payload
    metadata     JSONB       NOT NULL     DEFAULT '{}'::jsonb,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_location_metadata_user_time ON location_metadata (user_id, time_range);
CREATE INDEX idx_metadata_tags_gin ON location_metadata USING gin ((metadata->'tags'));
CREATE INDEX idx_metadata_companions_gin ON location_metadata USING gin ((metadata->'companions'));

ALTER TABLE processed_visits
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE trips
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;