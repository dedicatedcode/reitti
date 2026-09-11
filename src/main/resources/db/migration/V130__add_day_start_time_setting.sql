ALTER TABLE user_settings ADD COLUMN day_start_minutes INT DEFAULT 0;
ALTER TABLE user_settings ALTER COLUMN day_start_minutes SET NOT NULL;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_day_start_minutes_check CHECK (day_start_minutes BETWEEN 0 AND 1439);