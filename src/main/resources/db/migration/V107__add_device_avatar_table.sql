CREATE TABLE device_avatars
(
    user_id     BIGINT                  NOT NULL REFERENCES users (id),
    device_id   BIGINT                  NOT NULL REFERENCES devices (id),
    binary_data bytea,
    mime_type   varchar(255)            NOT NULL,
    updated_at  timestamp DEFAULT NOW() NOT NULL,
    CONSTRAINT unique_key UNIQUE (user_id, device_id)
);
