CREATE TABLE visits_to_processed_visits (
    visit_id BIGINT NOT NULL,
    processed_visit_id BIGINT NOT NULL,
    CONSTRAINT fk_visit_to_processed_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
    CONSTRAINT fk_processed_visit_to_visits FOREIGN KEY (processed_visit_id) REFERENCES processed_visits(id) ON DELETE CASCADE
)

-- create a insert statement which takes a comma seperated string in the column original_visit_ids splits it up and inserts it one by one into visits_to_processed_visits AI!