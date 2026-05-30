ALTER TABLE user_map_style_settings DROP COLUMN active_style_id;
ALTER TABLE user_map_style_settings ADD COLUMN active_style_id BIGINT REFERENCES public.user_map_styles(id) DEFAULT 1;
