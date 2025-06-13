-- Create immich_integrations table
CREATE TABLE immich_integrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    server_url VARCHAR(255) NOT NULL,
    api_token VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    CONSTRAINT fk_immich_integrations_user 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_immich_integrations_user_id ON immich_integrations(user_id);

-- Add unique constraint to ensure one integration per user
CREATE UNIQUE INDEX idx_immich_integrations_user_unique ON immich_integrations(user_id);
