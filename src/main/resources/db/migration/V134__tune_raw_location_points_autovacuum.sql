ALTER TABLE raw_location_points
    SET (autovacuum_vacuum_scale_factor = 0.02,
         autovacuum_vacuum_cost_delay = 0);