ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS friendly_name VARCHAR(255);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS enqueued_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS processing_at TIMESTAMP;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP;

-- Optional: Add index on the status column for faster queries
CREATE INDEX IF NOT EXISTS idx_import_jobs_status ON import_jobs(status);

ALTER TABLE import_jobs
    ADD COLUMN max_progress BIGINT;
ALTER TABLE import_jobs
    ADD COLUMN current_progress BIGINT;
ALTER TABLE import_jobs
    ADD COLUMN progress_message TEXT;

create table scheduled_tasks
(
    task_name            text                     not null,
    task_instance        text                     not null,
    task_data            bytea,
    execution_time       timestamp with time zone not null,
    picked               BOOLEAN                  not null,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures INT,
    last_heartbeat       timestamp with time zone,
    version              BIGINT                   not null,
    priority             SMALLINT,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx on scheduled_tasks (priority desc, execution_time asc);
