CREATE TABLE connected_users
(
    from_user BIGINT REFERENCES users (id),
    to_user   BIGINT REFERENCES users (id)
);

CREATE TABLE user_settings
(
    user_id            BIGINT UNIQUE REFERENCES users (id),
    prefer_colored_map BOOLEAN               DEFAULT FALSE,
    selected_language  VARCHAR(255) NOT NULL DEFAULT 'en'
);

CREATE TABLE user_avatars
(
    user_id     BIGINT UNIQUE REFERENCES users (id),
    binary_data BYTEA NULL
);

INSERT INTO user_settings (user_id, prefer_colored_map, selected_language)
SELECT id, false, 'en'
FROM users;