CREATE TABLE transport_mode_detection_configs (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transport_mode VARCHAR(255) NOT NULL,
    max_kmh DECIMAL,
    PRIMARY KEY (user_id, transport_mode)
);

CREATE TABLE transport_mode_overrides (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    time TIMESTAMP NOT NULL,
    transport_mode VARCHAR NOT NULL
);

INSERT INTO transport_mode_detection_configs(user_id, transport_mode, max_kmh) SELECT id, 'WALKING', 7.0 FROM users;
INSERT INTO transport_mode_detection_configs(user_id, transport_mode, max_kmh) SELECT id, 'CYCLING', 20.0 FROM users;
INSERT INTO transport_mode_detection_configs(user_id, transport_mode, max_kmh) SELECT id, 'DRIVING', 120.0 FROM users;
INSERT INTO transport_mode_detection_configs(user_id, transport_mode, max_kmh) SELECT id, 'TRANSIT', NULL FROM users;