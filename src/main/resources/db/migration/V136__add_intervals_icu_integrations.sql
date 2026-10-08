-- Per-user intervals.icu connection. One row per user.
-- intervals.icu is a hosted service, so there is no base_url: the API is always https://intervals.icu.
CREATE TABLE intervals_icu_integrations
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT        NOT NULL,
    api_key               TEXT          NOT NULL,
    reitti_device_id      BIGINT        NOT NULL,
    athlete_id            VARCHAR(64),
    athlete_name          VARCHAR(255),
    enabled               BOOLEAN       NOT NULL DEFAULT FALSE,
    last_successful_fetch TIMESTAMP NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 1,

    CONSTRAINT fk_intervals_icu_integrations_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_intervals_icu_integrations_device
        FOREIGN KEY (reitti_device_id) REFERENCES devices (id)
);

CREATE UNIQUE INDEX idx_intervals_icu_integrations_user_unique ON intervals_icu_integrations(user_id);

-- Bookkeeping for activities that have already been pulled over.
-- intervals.icu rate limits API key callers (5000 requests/day, 2500 per rolling 15 min),
-- so an activity must never be downloaded twice. The (user_id, activity_id) primary key
-- makes repeat imports impossible even when two syncs overlap.
CREATE TABLE intervals_icu_imported_activities
(
    user_id          BIGINT       NOT NULL,
    activity_id      VARCHAR(64)  NOT NULL,
    start_date_local TIMESTAMP NULL,
    points_received  BIGINT       NOT NULL DEFAULT 0,
    imported_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, activity_id),
    CONSTRAINT fk_intervals_icu_imported_activities_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
