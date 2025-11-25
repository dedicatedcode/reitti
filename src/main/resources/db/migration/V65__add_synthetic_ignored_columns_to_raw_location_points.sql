-- Add synthetic and ignored columns to raw_location_points table
ALTER TABLE raw_location_points 
ADD COLUMN synthetic BOOLEAN DEFAULT FALSE,
ADD COLUMN ignored BOOLEAN DEFAULT FALSE;

-- Add index for efficient querying of synthetic points
CREATE INDEX idx_raw_location_points_user_time_synthetic 
ON raw_location_points(user_id, timestamp, synthetic);

-- Add location density parameters to visit_detection_parameters table
ALTER TABLE visit_detection_parameters
ADD COLUMN density_max_interpolation_distance_meters DOUBLE PRECISION DEFAULT 50.0,
ADD COLUMN density_max_interpolation_gap_minutes INTEGER DEFAULT 720;

-- Add location density parameters to preview_visit_detection_parameters table
ALTER TABLE preview_visit_detection_parameters
ADD COLUMN density_max_interpolation_distance_meters DOUBLE PRECISION DEFAULT 50.0,
ADD COLUMN density_max_interpolation_gap_minutes INTEGER DEFAULT 720;
