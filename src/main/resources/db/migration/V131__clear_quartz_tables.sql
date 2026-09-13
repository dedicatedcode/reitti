-- Clears all Quartz tables as part of the migration from Java-serialized JobDataMaps
-- (useProperties=false) to String-only properties (useProperties=true).
-- Old BLOB job_data can no longer be deserialized after the format change, so all
-- scheduled triggers/jobs are dropped. Durable JobDetails are re-registered on startup
-- from TaskConfig/H3TaskConfig, transient tasks are re-enqueued by application logic.
-- All tables must be truncated in one statement because of the FK constraints between them.

TRUNCATE TABLE QRTZ_FIRED_TRIGGERS,
                QRTZ_PAUSED_TRIGGER_GRPS,
                QRTZ_SCHEDULER_STATE,
                QRTZ_LOCKS,
                QRTZ_SIMPLE_TRIGGERS,
                QRTZ_CRON_TRIGGERS,
                QRTZ_SIMPROP_TRIGGERS,
                QRTZ_BLOB_TRIGGERS,
                QRTZ_TRIGGERS,
                QRTZ_JOB_DETAILS,
                QRTZ_CALENDARS;

-- Orphaned non-terminal job metadata: the Quartz triggers backing these rows are gone.
-- Parent deletions cascade to child rows.
DELETE FROM job_meta_data WHERE status IN ('PREPARING', 'CREATED', 'AWAITING', 'RUNNING');
