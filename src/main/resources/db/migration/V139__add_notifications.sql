-- Notification framework: persistent in-app inbox, per-user settings,
-- per-type preferences and per-user delivery channels.

CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type_key   VARCHAR(64)  NOT NULL,
    severity   VARCHAR(16)  NOT NULL,
    title      TEXT         NOT NULL,
    message    TEXT         NOT NULL,
    params     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    action_url VARCHAR(1024),
    dedupe_key VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

-- collapses repeat events into a single unread entry per user/type/dedupe key
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_user_dedupe_unread
    ON notifications (user_id, type_key, dedupe_key)
    WHERE read_at IS NULL AND dedupe_key IS NOT NULL;

CREATE TABLE notification_settings
(
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    show_badge BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- missing row means "use the type default"
CREATE TABLE notification_type_preferences
(
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type_key   VARCHAR(64) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type_key)
);

CREATE TABLE notification_channels
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel_key VARCHAR(64) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    config      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 1,
    UNIQUE (user_id, channel_key)
);
