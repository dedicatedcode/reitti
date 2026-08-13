ALTER TABLE job_meta_data
    ALTER COLUMN enqueued_at TYPE timestamp(6) USING enqueued_at::timestamp(6);

ALTER TABLE job_meta_data
    ALTER COLUMN scheduled_at TYPE timestamp(6) USING scheduled_at::timestamp(6);

ALTER TABLE job_meta_data
    ALTER COLUMN processing_at TYPE timestamp(6) USING processing_at::timestamp(6);

ALTER TABLE job_meta_data
    ALTER COLUMN finished_at TYPE timestamp(6) USING finished_at::timestamp(6);

