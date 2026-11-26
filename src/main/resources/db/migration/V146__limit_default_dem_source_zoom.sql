UPDATE user_map_styles
SET style_json = jsonb_set(style_json::jsonb, '{sources,terrain-source,maxzoom}', '12'::jsonb)::text,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE default_style = TRUE
  AND style_json IS NOT NULL
  AND (style_json::jsonb #>> '{sources,terrain-source,maxzoom}')::INTEGER > 12;