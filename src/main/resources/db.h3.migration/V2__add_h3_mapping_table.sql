CREATE TABLE h3_mapping
(
    h3_index   h3index NOT NULL,
    first_seen_index BIGINT  NOT NULL, -- this 'references' raw_location_points
    first_seen  TIMESTAMP WITH TIME ZONE NOT NULL, -- this is a copy of the first seen indexes data for fast local lookups
    user_id BIGINT NOT NULL, -- this 'references' users, might need some cleanup now and then if users change
    UNIQUE (h3_index, user_id)
);

-- Stores which index we last mapped, this is used as a simple persistent datastore.
-- used for both: h3 mapping and significant place mapping
CREATE TYPE mapping_index_type AS ENUM ('raw_location_point_id');

CREATE TABLE latest_mapped_index
(
    mapping_index_type mapping_index_type NOT NULL UNIQUE PRIMARY KEY,
    latest_mapped_index BIGINT NOT NULL
);

INSERT INTO latest_mapped_index VALUES ('raw_location_point_id', -1);

--TODO: we might need to update h3_indices on point deletion.
--        This can have various reasons:
--          - synthetic points, which might be removed
--          - points marked as ignored later on (which somehow counts as a deletion)
--          - new feature allowing to remove data
--      This would lead to missing/invalid mappings.
--      Maybe instead of having a not null first seen make it nullable, set null on delete
--      Then we can check who else would have generated this point, if none: delete, else: update?
--      This would be a batched update job