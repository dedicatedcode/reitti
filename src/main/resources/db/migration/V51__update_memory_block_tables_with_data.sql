-- Drop existing foreign key constraints and recreate tables with embedded data

-- Drop old tables
DROP TABLE IF EXISTS memory_block_visit;
DROP TABLE IF EXISTS memory_block_trip;

-- Recreate memory_block_visit with embedded data
CREATE TABLE memory_block_visit (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    original_processed_visit_id BIGINT REFERENCES processed_visits(id) ON DELETE SET NULL,
    place_name VARCHAR(255),
    place_address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds BIGINT NOT NULL
);

CREATE INDEX idx_memory_block_visit_original_id ON memory_block_visit(original_processed_visit_id);
CREATE INDEX idx_memory_block_visit_start_time ON memory_block_visit(start_time);

-- Recreate memory_block_trip with embedded data
CREATE TABLE memory_block_trip (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    original_trip_id BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds BIGINT NOT NULL,
    estimated_distance_meters DOUBLE PRECISION,
    travelled_distance_meters DOUBLE PRECISION,
    transport_mode_inferred VARCHAR(50),
    start_place_name VARCHAR(255),
    start_latitude DOUBLE PRECISION,
    start_longitude DOUBLE PRECISION,
    end_place_name VARCHAR(255),
    end_latitude DOUBLE PRECISION,
    end_longitude DOUBLE PRECISION
);

CREATE INDEX idx_memory_block_trip_original_id ON memory_block_trip(original_trip_id);
CREATE INDEX idx_memory_block_trip_start_time ON memory_block_trip(start_time);
