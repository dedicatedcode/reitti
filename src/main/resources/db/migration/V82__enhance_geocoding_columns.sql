ALTER TABLE geocode_services RENAME COLUMN url_template TO url;
ALTER TABLE geocode_services ADD COLUMN type VARCHAR(255) DEFAULT 'GEOCODE_JSON';
ALTER TABLE geocode_services ADD COLUMN priority INT DEFAULT 1;
ALTER TABLE geocode_services ADD COLUMN additional_params TEXT NULL;

ALTER TABLE geocode_services ALTER COLUMN priority DROP DEFAULT;
ALTER TABLE geocode_services ALTER COLUMN type DROP DEFAULT;


INSERT INTO geocode_services(name, url, type, priority, enabled, additional_params) VALUES ('Paikka', 'https://geo.dedicatedcode.com', 'PAIKKA', 1, false, '{"limit": "5"}');