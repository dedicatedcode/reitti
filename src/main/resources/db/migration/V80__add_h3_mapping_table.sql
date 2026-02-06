CREATE TABLE h3_mapping
(
    h3_index   h3index NOT NULL UNIQUE,
    first_seen BIGINT  NOT NULL REFERENCES raw_location_points (id) ON DELETE CASCADE
);

ALTER TABLE raw_location_points
    ADD COLUMN h3_done BOOLEAN DEFAULT FALSE;
CREATE INDEX ON raw_location_points (h3_done);

--TODO: we might need to update h3_indices on point deletion.
--        This can have various reasons:
--          - synthetic points, which might be removed
--          - points marked as ignored later on (which somehow counts as a deletion)
--          - new feature allowing to remove data
--      This would lead to missing/invalid mappings.
--      Maybe instead of having a not null first seen make it nullable, set null on delete
--      Then we can check who else would have generated this point, if none: delete, else: update?
--      This would be a batched update job