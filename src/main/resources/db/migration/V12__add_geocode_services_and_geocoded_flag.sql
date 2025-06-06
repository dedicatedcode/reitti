-- Create geocode_services table
CREATE TABLE geocode_services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    url_template TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_used TIMESTAMP,
    last_error TIMESTAMP
);

-- Add geocoded column to significant_places table
ALTER TABLE significant_places ADD COLUMN geocoded BOOLEAN NOT NULL DEFAULT false;

-- Insert default OpenStreetMap Nominatim service
INSERT INTO geocode_services (name, url_template, enabled)
VALUES ('OpenStreetMap Nominatim', 'https://nominatim.openstreetmap.org/reverse?format=geocodejson&lat={lat}&lon={lng}', true);
