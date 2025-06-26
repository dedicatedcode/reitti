CREATE TABLE visits_to_processed_visits
(
    visit_id           BIGINT NOT NULL,
    processed_visit_id BIGINT NOT NULL,
    CONSTRAINT fk_visit_to_processed_visit FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE CASCADE,
    CONSTRAINT fk_processed_visit_to_visits FOREIGN KEY (processed_visit_id) REFERENCES processed_visits (id) ON DELETE CASCADE
);

-- Populate the join table from existing original_visit_ids data
INSERT INTO visits_to_processed_visits (visit_id, processed_visit_id)
SELECT CAST(TRIM(visit_id_str) AS BIGINT) as visit_id,
       pv.id                              as processed_visit_id
FROM processed_visits pv
         CROSS JOIN LATERAL unnest(string_to_array(pv.original_visit_ids, ',')) as visit_id_str
WHERE pv.original_visit_ids IS NOT NULL
  AND pv.original_visit_ids != ''
  AND TRIM(visit_id_str) != '';

ALTER TABLE processed_visits DROP COLUMN original_visit_ids;
ALTER TABLE processed_visits DROP COLUMN merged_count;

