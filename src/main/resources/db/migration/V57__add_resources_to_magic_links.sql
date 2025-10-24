ALTER TABLE magic_link_tokens ADD COLUMN resource_type VARCHAR(255) DEFAULT 'MAP';
ALTER TABLE magic_link_tokens ADD COLUMN resource_id BIGINT;

ALTER TABLE magic_link_tokens ALTER COLUMN resource_type DROP DEFAULT;