CREATE TABLE user_sharing (
    id BIGSERIAL PRIMARY KEY,
    sharing_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_with_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    color VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 1,
    UNIQUE(sharing_user_id, shared_with_user_id),
    CHECK (sharing_user_id != shared_with_user_id)
);

CREATE INDEX idx_user_sharing_sharing_user ON user_sharing(sharing_user_id);
CREATE INDEX idx_user_sharing_shared_with_user ON user_sharing(shared_with_user_id);
