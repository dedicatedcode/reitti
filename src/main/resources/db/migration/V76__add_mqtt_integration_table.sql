CREATE TABLE mqtt_integrations
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    host         VARCHAR(1024) NOT NULL,
    port         INTEGER       NOT NULL,
    identifier   VARCHAR(1024) NOT NULL,
    topic        VARCHAR(1024) NOT NULL,
    username     VARCHAR(1024) NOT NULL,
    password     VARCHAR(1024) NOT NULL,
    payload_type VARCHAR(1024) NOT NULL,
    enabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NULL,
    last_used    TIMESTAMP     NULL,
    version      BIGINT        NOT NULL,
    CONSTRAINT fk_mqtt_integrations_user FOREIGN KEY (user_id) REFERENCES users (id)
);
