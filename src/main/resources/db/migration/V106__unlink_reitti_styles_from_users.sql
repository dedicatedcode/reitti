ALTER TABLE user_map_styles ALTER COLUMN user_id DROP NOT NULL;

UPDATE user_map_styles SET user_id = NULL WHERE name = 'Reitti';
UPDATE user_map_styles SET user_id = NULL WHERE name = 'Reitti (Colored)';