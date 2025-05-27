-- Add password column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);

-- Create admin user with password 'admin' (BCrypt encoded)
-- First check if the user exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE username = 'admin') THEN
        UPDATE users SET password = '$2a$10$rXXm9rFzQeJLJSP7TE3LMO9vWRlW.ZKLEw1YNECzk.FC4lzOVzRIe' WHERE username = 'admin';
    ELSE
        INSERT INTO users (username, display_name, password) 
        VALUES ('admin', 'Administrator', '$2a$10$rXXm9rFzQeJLJSP7TE3LMO9vWRlW.ZKLEw1YNECzk.FC4lzOVzRIe');
    END IF;
END $$;
