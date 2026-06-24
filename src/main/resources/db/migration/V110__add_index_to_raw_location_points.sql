CREATE INDEX idx_rlp_active_users
    ON raw_location_points (user_id, timestamp)
    WHERE processed = false;