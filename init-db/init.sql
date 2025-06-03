-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create schema
CREATE SCHEMA IF NOT EXISTS reitti;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE reittidb TO reitti;
ALTER USER reitti WITH SUPERUSER;
