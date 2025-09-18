CREATE SEQUENCE preview_processed_visits_id_seq;

CREATE TABLE preview_processed_visits (
    LIKE processed_visits INCLUDING ALL
);

CREATE TABLE preview_raw_location_points (
    LIKE raw_location_points INCLUDING ALL
);

CREATE TABLE preview_trips (
    LIKE trips INCLUDING ALL
);

CREATE TABLE preview_visits (
    LIKE visits INCLUDING ALL
);

ALTER TABLE preview_processed_visits ADD COLUMN preview_id VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE preview_processed_visits ALTER COLUMN id SET DEFAULT nextval('preview_processed_visits_id_seq'::regclass);
ALTER SEQUENCE preview_processed_visits_id_seq OWNED BY preview_processed_visits.id;

ALTER TABLE preview_raw_location_points ADD COLUMN preview_id VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE preview_trips ADD COLUMN preview_id VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE preview_visits ADD COLUMN preview_id VARCHAR(36) NOT NULL DEFAULT '';