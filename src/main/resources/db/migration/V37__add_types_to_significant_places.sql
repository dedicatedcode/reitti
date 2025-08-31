-- Add new columns to significant_place
ALTER TABLE significant_places
    ADD COLUMN type       VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN country_code VARCHAR(10) NULL;

ALTER TABLE significant_places DROP COLUMN category;

-- Create geocoding_response table
CREATE TABLE geocoding_response
(
    id                   BIGSERIAL PRIMARY KEY,
    significant_place_id BIGINT REFERENCES significant_places (id),
    raw_data             JSONB,
    provider_name        VARCHAR(100),
    fetched_at           TIMESTAMP WITH TIME ZONE,
    status               VARCHAR(50),
    error_details        TEXT,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_geocoding_response_place_id ON geocoding_response (significant_place_id);
CREATE INDEX idx_geocoding_response_status ON geocoding_response (status);
CREATE INDEX idx_significant_place_type ON significant_places (type);
CREATE INDEX idx_significant_place_country ON significant_places (country_code);