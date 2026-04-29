ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS friendly_name VARCHAR(255);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS enqueued_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS processing_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP;

-- Optional: Add index on the status column for faster queries
CREATE INDEX IF NOT EXISTS idx_import_jobs_status ON import_jobs(status);