CREATE TABLE h3_mapping
(
    h3_index   h3index NOT NULL UNIQUE,
    first_seen_index BIGINT  NOT NULL, -- this 'references' raw_location_points
    first_seen  TIMESTAMP WITH TIME ZONE NOT NULL -- this is a copy of the first seen indexes data for fast local lookups
);

-- Stores which index we last mapped to h3, this is used as a simple persistent datastore
-- normally this should only have one row, if not, always use highest values. This marks the last raw_location_points index we mapped to h3
CREATE TABLE latest_mapped_index
(
    latest_mapped_index BIGINT NOT NULL
);

--TODO: we might need to update h3_indices on point deletion.
--        This can have various reasons:
--          - synthetic points, which might be removed
--          - points marked as ignored later on (which somehow counts as a deletion)
--          - new feature allowing to remove data
--      This would lead to missing/invalid mappings.
--      Maybe instead of having a not null first seen make it nullable, set null on delete
--      Then we can check who else would have generated this point, if none: delete, else: update?
--      This would be a batched update job