ALTER TABLE visits
    ADD CONSTRAINT visits_pk
        UNIQUE (user_id, start_time, end_time);
