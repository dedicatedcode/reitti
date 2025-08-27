
CREATE TABLE remote_user_info
(
    integration_id BIGINT UNIQUE REFERENCES reitti_integrations(id) ON DELETE CASCADE,
    remote_id BIGINT NOT NULL,
    remote_version INTEGER NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(1024),
    binary_data BYTEA NULL,
    mime_type VARCHAR(255) NULL
);

