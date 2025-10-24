-- Create memory table
CREATE TABLE memory (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    header_type VARCHAR(50) NOT NULL,
    header_image_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT memory_date_range_check CHECK (end_date >= start_date)
);

CREATE INDEX idx_memory_user_id ON memory(user_id);
CREATE INDEX idx_memory_date_range ON memory(start_date, end_date);
CREATE INDEX idx_memory_created_at ON memory(created_at DESC);

-- Create memory_block table
CREATE TABLE memory_block (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES memory(id) ON DELETE CASCADE,
    block_type VARCHAR(50) NOT NULL,
    position INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT memory_block_position_check CHECK (position >= 0),
    CONSTRAINT memory_block_unique_position UNIQUE (memory_id, position)
);

CREATE INDEX idx_memory_block_memory_id ON memory_block(memory_id);
CREATE INDEX idx_memory_block_position ON memory_block(memory_id, position);

-- Create memory_block_visit table
CREATE TABLE memory_block_visit (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    visit_id BIGINT NOT NULL REFERENCES processed_visits(id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_block_visit_visit_id ON memory_block_visit(visit_id);

-- Create memory_block_trip table
CREATE TABLE memory_block_trip (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_block_trip_trip_id ON memory_block_trip(trip_id);

-- Create memory_block_text table
CREATE TABLE memory_block_text (
    block_id BIGINT PRIMARY KEY REFERENCES memory_block(id) ON DELETE CASCADE,
    headline VARCHAR(255),
    content TEXT
);

-- Create memory_block_image_gallery table
CREATE TABLE memory_block_image_gallery (
    id BIGSERIAL PRIMARY KEY,
    block_id BIGINT NOT NULL REFERENCES memory_block(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    caption TEXT,
    position INTEGER NOT NULL,
    CONSTRAINT memory_block_image_gallery_position_check CHECK (position >= 0),
    CONSTRAINT memory_block_image_gallery_unique_position UNIQUE (block_id, position)
);

CREATE INDEX idx_memory_block_image_gallery_block_id ON memory_block_image_gallery(block_id);
CREATE INDEX idx_memory_block_image_gallery_position ON memory_block_image_gallery(block_id, position);
