CREATE TABLE location_metadata
(
    id           SERIAL PRIMARY KEY,
    -- Acts purely as a hint/context for the UI or recalculation filter
    context_type VARCHAR(10) NOT NULL, -- 'TRIP' or 'VISIT'
    -- The absolute temporal anchor
    time_range   TSTZRANGE   NOT NULL,
    -- The user data payload
    metadata     JSONB       NOT NULL     DEFAULT '{}'::jsonb,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_override_time_range ON location_metadata USING GIST (time_range);

ALTER TABLE processed_visits
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE trips
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;