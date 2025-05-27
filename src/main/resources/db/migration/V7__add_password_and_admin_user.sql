-- Add password column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);

-- Create admin user with password 'admin' (BCrypt encoded)
INSERT INTO users (username, display_name, password)
VALUES ('admin', 'Administrator', '$2a$10$rXXm9rFzQeJLJSP7TE3LMO9vWRlW.ZKLEw1YNECzk.FC4lzOVzRIe')
ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password;