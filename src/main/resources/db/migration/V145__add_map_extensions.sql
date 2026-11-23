CREATE TABLE map_extensions (
    id BIGSERIAL PRIMARY KEY,
    manifest_id VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    version VARCHAR(64) NOT NULL,
    manifest_url TEXT NOT NULL,
    manifest JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    encrypted_credentials TEXT,
    revision BIGINT NOT NULL DEFAULT 1,
    last_refreshed_at TIMESTAMP WITH TIME ZONE,
    last_refresh_success BOOLEAN,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT map_extensions_manifest_id_unique UNIQUE (manifest_id),
    CONSTRAINT map_extensions_manifest_url_unique UNIQUE (manifest_url)
);

CREATE INDEX idx_map_extensions_enabled ON map_extensions(enabled);
