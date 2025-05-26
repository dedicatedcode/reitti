CREATE TABLE api_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT fk_api_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_api_token_token ON api_tokens(token);
