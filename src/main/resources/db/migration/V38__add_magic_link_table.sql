CREATE TABLE magic_link_tokens
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name   VARCHAR(1024) NOT NULL,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    access_level VARCHAR(255) NOT NULL,
    expiry_date  TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL,
    last_used_at TIMESTAMP,
    CONSTRAINT fk_magic_link_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_magic_link_token_token ON magic_link_tokens (token_hash);

CREATE TABLE magic_link_tokens_usages
(
    token_id BIGINT REFERENCES magic_link_tokens(id) ON DELETE CASCADE,
    at TIMESTAMP NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    ip VARCHAR(255) NOT NULL
);