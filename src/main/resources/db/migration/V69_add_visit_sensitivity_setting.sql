INSERT INTO visit_detection_parameters(user_id, valid_since, detection_minimum_stay_time_seconds,
                                       detection_max_merge_time_between_same_stay_points,
                                       merging_search_duration_in_hours, merging_max_merge_time_between_same_visits,
                                       merging_min_distance_between_visits, density_max_interpolation_distance_meters,
                                       density_max_interpolation_gap_minutes)
SELECT id,
       date_trunc('day', now()),
       300,
       300,
       24,
       300,
       100,
       50,
       720
FROM users;