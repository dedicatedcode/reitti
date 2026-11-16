-- Synthetic gap-fill points are now generated in memory and are no longer persisted.
-- The guards keep this migration safe if the destructive part was already applied manually.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'raw_location_points' AND column_name = 'synthetic') THEN
        DELETE FROM raw_location_points WHERE synthetic = true;
        ALTER TABLE raw_location_points DROP COLUMN synthetic;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'preview_raw_location_points' AND column_name = 'synthetic') THEN
        DELETE FROM preview_raw_location_points WHERE synthetic = true;
        ALTER TABLE preview_raw_location_points DROP COLUMN synthetic;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_raw_location_points_user_time_synthetic;
