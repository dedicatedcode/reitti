-- Add version columns for optimistic locking to all entity tables

ALTER TABLE users ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE visits ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE trips ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE significant_places ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE raw_location_points ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE immich_integrations ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE geocode_services ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE api_tokens ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE processed_visits ADD COLUMN version BIGINT DEFAULT 0;

-- Update existing records to have version 0
UPDATE users SET version = 0 WHERE version IS NULL;
UPDATE visits SET version = 0 WHERE version IS NULL;
UPDATE trips SET version = 0 WHERE version IS NULL;
UPDATE significant_places SET version = 0 WHERE version IS NULL;
UPDATE raw_location_points SET version = 0 WHERE version IS NULL;
UPDATE immich_integrations SET version = 0 WHERE version IS NULL;
UPDATE geocode_services SET version = 0 WHERE version IS NULL;
UPDATE api_tokens SET version = 0 WHERE version IS NULL;
UPDATE processed_visits SET version = 0 WHERE version IS NULL;

-- Make version columns NOT NULL after setting default values
ALTER TABLE users ALTER COLUMN version SET NOT NULL;
ALTER TABLE visits ALTER COLUMN version SET NOT NULL;
ALTER TABLE trips ALTER COLUMN version SET NOT NULL;
ALTER TABLE significant_places ALTER COLUMN version SET NOT NULL;
ALTER TABLE raw_location_points ALTER COLUMN version SET NOT NULL;
ALTER TABLE immich_integrations ALTER COLUMN version SET NOT NULL;
ALTER TABLE geocode_services ALTER COLUMN version SET NOT NULL;
ALTER TABLE api_tokens ALTER COLUMN version SET NOT NULL;
ALTER TABLE processed_visits ALTER COLUMN version SET NOT NULL;
