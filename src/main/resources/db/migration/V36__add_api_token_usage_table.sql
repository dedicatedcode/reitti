CREATE TABLE api_token_usages
(
    token_id BIGINT REFERENCES api_tokens(id) ON DELETE CASCADE,
    at TIMESTAMP NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    ip VARCHAR(255) NOT NULL
);

