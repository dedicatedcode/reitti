ALTER TABLE user_settings
    ADD COLUMN time_zone_override TEXT         NULL,
    ADD COLUMN time_display_mode  VARCHAR(255) NOT NULL DEFAULT 'DEFAULT'