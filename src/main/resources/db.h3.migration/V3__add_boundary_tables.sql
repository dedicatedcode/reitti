CREATE TYPE area_type AS ENUM ('country', 'state', 'county', 'city', 'reserved');

CREATE TABLE area
(
    id                 SERIAL PRIMARY KEY,
    type               area_type NOT NULL,
    name               TEXT      NOT NULL,
    -- Parent id to build hierarchical areas: country -> state -> county -> city
    parent             BIGINT REFERENCES area (id) ON DELETE CASCADE,
    boundary           geometry(MultiPolygon, 4326),
    last_checked       TIMESTAMP NOT NULL DEFAULT now(),
    boundaries_checked BOOLEAN   NOT NULL DEFAULT false,
    CONSTRAINT enforce_valid_geom CHECK (ST_IsValid(boundary)),
    UNIQUE (type, name, parent)
);
CREATE INDEX idx_area_geom_gist ON area USING GIST (boundary) WHERE boundary IS NOT NULL;

-- Insert "no_area" areaDescription we can use to mark a point as "done" but keep it out of requests for unmapped h3 indices
-- Id -1 is thus reserved and should only be used to mark significant places we checked the areaDescription for but have none
INSERT INTO area (id, type, name, parent, boundary)
VALUES (-1,
        'reserved',
        'no_area',
        null,
        null);

CREATE TABLE area_significant_place_mapping
(
    area_id  BIGINT NOT NULL REFERENCES area (id) ON DELETE CASCADE,
    place_id BIGINT NOT NULL, -- references significant_places(id), on deletion can be cascaded
    PRIMARY KEY (area_id, place_id)
);

-- Add areaDescription references to h3_mapping
ALTER TABLE h3_mapping
    ADD COLUMN city_id    BIGINT REFERENCES area (id) ON DELETE SET NULL,
    ADD COLUMN country_id BIGINT REFERENCES area (id) ON DELETE SET NULL,
    ADD COLUMN state_id   BIGINT REFERENCES area (id) ON DELETE SET NULL,
    ADD COLUMN county_id  BIGINT REFERENCES area (id) ON DELETE SET NULL;

ALTER TYPE mapping_index_type ADD VALUE 'significant_place_id';