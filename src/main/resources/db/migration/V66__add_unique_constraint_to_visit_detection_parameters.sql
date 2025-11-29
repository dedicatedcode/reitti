ALTER TABLE visit_detection_parameters
    DROP CONSTRAINT IF EXISTS user_valid_since_pk;

-- Step 2: Remove duplicate entries (keeping the newest by id)
WITH duplicates AS (
    SELECT ctid,
           ROW_NUMBER() OVER (
               PARTITION BY
                   user_id,
                   COALESCE(valid_since::text, '<<<NULL>>>')
               ORDER BY id DESC
               ) AS rn
    FROM visit_detection_parameters
)
DELETE FROM visit_detection_parameters
WHERE ctid IN (
    SELECT ctid FROM duplicates WHERE rn > 1
);

-- Step 3: Re-create the unique constraint with NULLS NOT DISTINCT
ALTER TABLE visit_detection_parameters
    ADD CONSTRAINT user_valid_since_pk
        UNIQUE NULLS NOT DISTINCT (user_id, valid_since);