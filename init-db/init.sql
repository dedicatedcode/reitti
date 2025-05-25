-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Create schema
CREATE SCHEMA IF NOT EXISTS reitti;

-- Grant privileges
GRANT ALL PRIVILEGES ON SCHEMA reitti TO reitti;
