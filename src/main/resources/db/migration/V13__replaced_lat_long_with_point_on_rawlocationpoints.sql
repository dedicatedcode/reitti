ALTER TABLE raw_location_points ADD COLUMN geom geometry(Point, 4326);
UPDATE raw_location_points SET geom = st_setsrid(st_makepoint(longitude, latitude), 4326);

ALTER TABLE raw_location_points DROP COLUMN latitude;
ALTER TABLE raw_location_points DROP COLUMN longitude;