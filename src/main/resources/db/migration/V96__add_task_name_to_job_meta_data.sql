ALTER TABLE import_jobs ADD COLUMN task_id VARCHAR(255);
ALTER TABLE import_jobs RENAME TO job_meta_data;
