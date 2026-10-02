-- trips had no index besides its primary key: every processed visit deleted by the pipeline cascaded into two
-- full scans of trips (start_visit_id/end_visit_id), and each trip lookup of a batch scanned all trips of all users.
CREATE INDEX IF NOT EXISTS idx_trips_start_visit_id ON trips (start_visit_id);
CREATE INDEX IF NOT EXISTS idx_trips_end_visit_id ON trips (end_visit_id);
CREATE INDEX IF NOT EXISTS idx_trips_user_start_time ON trips (user_id, start_time);
CREATE INDEX IF NOT EXISTS idx_trips_user_end_time ON trips (user_id, end_time);

-- overlap lookups and the visit before/after a processing window, without reading all visits of the user
CREATE INDEX IF NOT EXISTS idx_processed_visits_user_start_time ON processed_visits (user_id, start_time);
CREATE INDEX IF NOT EXISTS idx_processed_visits_user_end_time ON processed_visits (user_id, end_time);
