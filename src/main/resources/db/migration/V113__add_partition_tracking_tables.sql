CREATE TABLE IF NOT EXISTS partition_registry
(
    partition_name TEXT PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Populate the registry with all current partitions
DO
$$
    DECLARE
        partition_record RECORD;
        v_full_name      TEXT;
        v_extracted_id   TEXT;
    BEGIN
        FOR partition_record IN
            SELECT c.relname::text AS table_name
            FROM pg_inherits i
                     JOIN pg_class c ON c.oid = i.inhrelid
                     JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.relname = 'staging_location_points'
            LOOP
                v_full_name := partition_record.table_name;

                -- Strip 'staged_' prefix.
                -- If the name doesn't have the prefix, it handles it gracefully.
                v_extracted_id := REGEXP_REPLACE(v_full_name, '^staged_', '');

                INSERT INTO partition_registry (partition_name, created_at)
                VALUES (v_extracted_id, NOW())
                ON CONFLICT (partition_name) DO NOTHING;
            END LOOP;
    END
$$;