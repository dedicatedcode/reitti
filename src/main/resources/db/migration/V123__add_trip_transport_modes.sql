CREATE TABLE trip_transport_modes
(
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    trip_id             BIGINT           NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    offset_seconds      BIGINT           NOT NULL,
    duration_in_seconds BIGINT           NOT NULL,
    transportation_mode VARCHAR          NOT NULL,
    distance_meters     DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_trip_transport_modes_trip_id ON trip_transport_modes (trip_id);

CREATE TABLE preview_trip_transport_modes
(
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    trip_id             BIGINT           NOT NULL REFERENCES preview_trips (id) ON DELETE CASCADE,
    offset_seconds      BIGINT           NOT NULL,
    duration_in_seconds BIGINT           NOT NULL,
    transportation_mode VARCHAR          NOT NULL,
    distance_meters     DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_preview_trip_transport_modes_trip_id ON preview_trip_transport_modes (trip_id);

INSERT INTO trip_transport_modes (trip_id, offset_seconds, duration_in_seconds, transportation_mode, distance_meters)
SELECT id, 0, duration_seconds, transport_mode_inferred, COALESCE(travelled_distance_meters, 0.0)
FROM trips
WHERE transport_mode_inferred IS NOT NULL;

INSERT INTO preview_trip_transport_modes (trip_id, offset_seconds, duration_in_seconds, transportation_mode, distance_meters)
SELECT id, 0, duration_seconds, transport_mode_inferred, COALESCE(travelled_distance_meters, 0.0)
FROM preview_trips
WHERE transport_mode_inferred IS NOT NULL;

ALTER TABLE trips
    DROP COLUMN transport_mode_inferred;

ALTER TABLE preview_trips
    DROP COLUMN transport_mode_inferred;

ALTER TABLE transport_mode_detection_configs
    ADD COLUMN color VARCHAR(50);

ALTER TABLE transport_mode_detection_configs
    ADD COLUMN icon VARCHAR(50);
