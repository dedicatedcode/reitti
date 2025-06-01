ALTER TABLE visits
    ADD COLUMN latitude float(53);
ALTER TABLE visits
    ADD COLUMN longitude float(53);

UPDATE visits
SET latitude = subquery.latitude_centroid,
    longitude = subquery.longitude_centroid
FROM (
         SELECT id, latitude_centroid, longitude_centroid
         FROM significant_places
     ) AS subquery
WHERE visits.place_id = subquery.id;


-- Drop foreign key constraint
ALTER TABLE visits
    DROP CONSTRAINT IF EXISTS fk_visits_place;

-- Drop the place_id column
ALTER TABLE visits
    DROP COLUMN IF EXISTS place_id;
