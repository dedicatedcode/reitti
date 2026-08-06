DELETE FROM qrtz_fired_triggers
WHERE (trigger_name, trigger_group) IN (
    SELECT trigger_name, trigger_group FROM qrtz_triggers WHERE job_name = 'sse-emitter-job'
);

DELETE FROM qrtz_simple_triggers
WHERE (trigger_name, trigger_group) IN (
    SELECT trigger_name, trigger_group FROM qrtz_triggers WHERE job_name = 'sse-emitter-job'
);

DELETE FROM qrtz_simprop_triggers
WHERE (trigger_name, trigger_group) IN (
    SELECT trigger_name, trigger_group FROM qrtz_triggers WHERE job_name = 'sse-emitter-job'
);

DELETE FROM qrtz_cron_triggers
WHERE (trigger_name, trigger_group) IN (
    SELECT trigger_name, trigger_group FROM qrtz_triggers WHERE job_name = 'sse-emitter-job'
);

DELETE FROM qrtz_triggers
WHERE job_name = 'sse-emitter-job';

DELETE FROM qrtz_job_details
WHERE job_name = 'sse-emitter-job';

DELETE FROM job_meta_data
WHERE type = 'SSE_EVENT';
