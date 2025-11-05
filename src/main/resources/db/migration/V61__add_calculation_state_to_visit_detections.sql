ALTER TABLE visit_detection_parameters ADD COLUMN recalculation_state TEXT DEFAULT 'DONE';
UPDATE visit_detection_parameters SET recalculation_state = 'NEEDED' WHERE needs_recalculation = true;
ALTER TABLE visit_detection_parameters DROP COLUMN needs_recalculation;