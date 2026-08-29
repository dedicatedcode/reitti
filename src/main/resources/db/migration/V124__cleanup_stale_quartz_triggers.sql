CREATE TEMP TABLE stale_triggers ON COMMIT DROP AS
SELECT t.trigger_name, t.trigger_group
FROM qrtz_triggers t
WHERE t.trigger_group = 'reitti-tasks'
  AND (
    NOT EXISTS (SELECT 1 FROM job_meta_data jmd WHERE jmd.id::text = t.trigger_name)
    OR EXISTS (SELECT 1
               FROM job_meta_data jmd
               WHERE jmd.id::text = t.trigger_name
                 AND jmd.status IN ('COMPLETED', 'FAILED', 'CANCELLED'))
    OR (position(':' IN t.trigger_name) > 0
        AND EXISTS (SELECT 1
                    FROM job_meta_data jmd
                    WHERE jmd.id::text = split_part(t.trigger_name, ':', 1)
                      AND jmd.status IN ('COMPLETED', 'FAILED', 'CANCELLED')))
  );

DELETE FROM qrtz_fired_triggers f
USING stale_triggers s
WHERE f.trigger_name = s.trigger_name AND f.trigger_group = s.trigger_group;

DELETE FROM qrtz_simple_triggers st
USING stale_triggers s
WHERE st.trigger_name = s.trigger_name AND st.trigger_group = s.trigger_group;

DELETE FROM qrtz_simprop_triggers sp
USING stale_triggers s
WHERE sp.trigger_name = s.trigger_name AND sp.trigger_group = s.trigger_group;

DELETE FROM qrtz_cron_triggers ct
USING stale_triggers s
WHERE ct.trigger_name = s.trigger_name AND ct.trigger_group = s.trigger_group;

DELETE FROM qrtz_blob_triggers bt
USING stale_triggers s
WHERE bt.trigger_name = s.trigger_name AND bt.trigger_group = s.trigger_group;

DELETE FROM qrtz_triggers t
USING stale_triggers s
WHERE t.trigger_name = s.trigger_name AND t.trigger_group = s.trigger_group;

-- Dead job metadata: jobs that never started (AWAITING/CREATED) and have no trigger left
-- that could ever run them. These would linger forever since the periodic metadata cleanup
-- only removes metadata of jobs that reached a terminal state.
DELETE FROM job_meta_data jmd
WHERE jmd.status IN ('AWAITING', 'CREATED')
  AND NOT EXISTS (SELECT 1
                  FROM qrtz_triggers t
                  WHERE t.trigger_group = 'reitti-tasks'
                    AND (t.trigger_name = jmd.id::text OR t.trigger_name LIKE jmd.id::text || ':%'));
