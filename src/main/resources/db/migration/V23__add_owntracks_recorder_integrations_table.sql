-- Create owntracks_recorder_integration table
CREATE TABLE owntracks_recorder_integration (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT NOT NULL,
                                     base_url VARCHAR(255) NOT NULL,
                                     username VARCHAR(255) NOT NULL,
                                     device_id VARCHAR(255) NOT NULL,
                                     enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                     last_successful_fetch TIMESTAMP NULL,
                                     version BIGINT NOT NULL,

                                     CONSTRAINT fk_owntracks_recorder_integration_user
                                         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_owntracks_recorder_integration_user_id ON owntracks_recorder_integration(user_id);

-- Add unique constraint to ensure one integration per user
CREATE UNIQUE INDEX idx_owntracks_recorder_integration_user_unique ON owntracks_recorder_integration(user_id);
