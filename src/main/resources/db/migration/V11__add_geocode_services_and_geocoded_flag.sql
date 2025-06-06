-- Create geocode_services table
CREATE TABLE geocode_services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    url_template TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    error_count INTEGER NOT NULL DEFAULT 0,
    max_errors INTEGER NOT NULL DEFAULT 10,
    last_used TIMESTAMP,
    last_error TIMESTAMP,
    is_default BOOLEAN NOT NULL DEFAULT false
);

-- Add geocoded column to significant_places table
ALTER TABLE significant_places ADD COLUMN geocoded BOOLEAN NOT NULL DEFAULT false;

-- Insert default OpenStreetMap Nominatim service
INSERT INTO geocode_services (name, url_template, enabled, is_default, max_errors) 
VALUES ('OpenStreetMap Nominatim', 'https://nominatim.openstreetmap.org/reverse?format=json&lat={lat}&lon={lng}&zoom=18&addressdetails=1', true, true, 10);
