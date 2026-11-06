-- Per user/device configuration for device inactivity notifications.
-- A row only exists while the user has notifications enabled for that device.
-- last_notified_at implements the rising-flank behaviour: it is cleared as soon as
-- the device sends data again, so the next inactivity period notifies once more.

CREATE TABLE device_inactivity_notifications
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id        BIGINT      NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    hours            INT         NOT NULL CHECK (hours > 0 AND hours <= 8760),
    last_notified_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 1,
    UNIQUE (user_id, device_id)
);
