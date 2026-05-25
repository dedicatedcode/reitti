CREATE TABLE user_map_styles
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    map_type          VARCHAR(32)  NOT NULL DEFAULT 'vector',
    style_input_type  VARCHAR(32)  NOT NULL DEFAULT 'url',
    raster_source_input_type VARCHAR(32) NOT NULL DEFAULT 'tile_template',
    style_json        TEXT,
    style_url         TEXT,
    source_id         VARCHAR(255),
    source_type       VARCHAR(32)  NOT NULL DEFAULT 'vector',
    tilejson_url      TEXT,
    tile_url_template TEXT,
    attribution       TEXT,
    minzoom           INTEGER,
    maxzoom           INTEGER,
    tile_size         INTEGER,
    scheme            VARCHAR(8),
    attribution_override TEXT,
    glyphs_url_override TEXT,
    sprite_url_override TEXT,
    shared            BOOLEAN      NOT NULL DEFAULT FALSE,
    proxy_tiles       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT user_map_styles_style_source CHECK (
        (map_type = 'vector' AND (style_json IS NOT NULL OR style_url IS NOT NULL))
        OR
        (map_type = 'raster' AND (tilejson_url IS NOT NULL OR tile_url_template IS NOT NULL))
    )
);

CREATE INDEX idx_user_map_styles_user_id ON user_map_styles (user_id);
CREATE INDEX idx_user_map_styles_shared ON user_map_styles (shared) WHERE shared = TRUE;

CREATE TABLE user_map_style_settings
(
    user_id         BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    active_style_id VARCHAR(255) NOT NULL DEFAULT 'reitti',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
