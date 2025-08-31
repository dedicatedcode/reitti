-- Cleanup script for integration tests
DELETE FROM significant_places;
DELETE FROM users;
-- Reset sequences
ALTER SEQUENCE significant_places_id_seq RESTART WITH 1;
ALTER SEQUENCE users_id_seq RESTART WITH 1;
