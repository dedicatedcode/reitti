DELETE FROM processed_visits a USING (
    SELECT MIN(ctid) as ctid, user_id, place_id, start_time, end_time
    FROM processed_visits
    GROUP BY (user_id, place_id, start_time, end_time) HAVING COUNT(*) > 1
) b
WHERE a.user_id = b.user_id
  AND a.place_id = b.place_id
  AND a.start_time = b.start_time
  AND a.end_time = b.end_time
  AND a.ctid <> b.ctid;

ALTER TABLE processed_visits
    ADD CONSTRAINT processed_visits_pk
        UNIQUE (user_id, place_id, start_time, end_time);

