DELETE FROM trips;
DELETE FROM raw_location_points;
DELETE FROM processed_visits;
DELETE FROM visits;

ALTER TABLE trips ADD COLUMN start_visit_id bigint not null;
ALTER TABLE trips ADD COLUMN end_visit_id bigint not null;

alter table if exists trips
    add constraint FK8wb14dx6dasdasasd3planbay88u
        foreign key (start_visit_id)
            references processed_visits ON DELETE CASCADE ;

alter table if exists trips
    add constraint FK8wb14dx6dasdasasd3planasdd12u
        foreign key (end_visit_id)
            references processed_visits ON DELETE CASCADE ;


