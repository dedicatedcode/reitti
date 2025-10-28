CREATE TABLE memory_visits (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_id BIGINT NULL REFERENCES visits(id) ON DELETE SET NULL,
    memory_block_id BIGINT NOT NULL REFERENCES memory_block(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    latitude_centroid double precision not null,
    longitude_centroid double precision not null
);

CREATE TABLE memory_trips (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_id BIGINT NULL REFERENCES trips(id) ON DELETE SET NULL,
    memory_block_id BIGINT NOT NULL REFERENCES memory_block(id) ON DELETE CASCADE,
    start_visit_id BIGINT REFERENCES memory_visits(id) ON DELETE CASCADE,
    end_visit_id BIGINT REFERENCES memory_visits(id) ON DELETE CASCADE,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL
);

