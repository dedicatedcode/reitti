ALTER TABLE geocode_services ADD COLUMN type VARCHAR(255) DEFAULT 'GEOCODE_JSON';
ALTER TABLE geocode_services ADD COLUMN priority INT DEFAULT 1;

ALTER TABLE geocode_services ALTER COLUMN priority DROP DEFAULT;
ALTER TABLE geocode_services ALTER COLUMN type DROP DEFAULT;

INSERT INTO geocode_services(name, url_template, type, priority, enabled) VALUES ('Paikka', 'https://geo.dedicatedcode.com?lat=(lat)&lon={lng}&limit=5', 'PAIKKA', 1, false);