-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Create schema
CREATE SCHEMA IF NOT EXISTS reitti;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE reittidb TO reitti;
ALTER USER reitti WITH SUPERUSER;
