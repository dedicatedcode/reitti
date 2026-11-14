package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.CoverageRegion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CoverageRegionJdbcService {

    private static final int NAME_BATCH_SIZE = 1000;
    private static final int MAX_PATH_DEPTH = 16;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;

    private final JdbcTemplate jdbcTemplate;

    public CoverageRegionJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final org.springframework.jdbc.core.RowMapper<CoverageRegion> rowMapper = (rs, _) -> new CoverageRegion(
            rs.getLong("osm_id"),
            rs.getObject("parent_osm_id", Long.class),
            CoverageRegion.RegionKind.valueOf(rs.getString("kind")),
            rs.getInt("admin_level"),
            rs.getInt("resolution"),
            rs.getLong("total_cells"),
            rs.getInt("depth"),
            rs.getInt("child_count"),
            rs.getString("continent"),
            rs.getString("name_key")
    );

    public List<CoverageRegion> findRoots() {
        return jdbcTemplate.query("SELECT * FROM coverage_region WHERE parent_osm_id IS NULL", rowMapper);
    }

    public Optional<CoverageRegion> findById(long osmId) {
        return jdbcTemplate.query("SELECT * FROM coverage_region WHERE osm_id = ?", rowMapper, osmId)
                .stream()
                .findFirst();
    }

    public List<CoverageRegion> findChildren(long parentOsmId) {
        return jdbcTemplate.query("SELECT * FROM coverage_region WHERE parent_osm_id = ?", rowMapper, parentOsmId);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coverage_region", Long.class);
        return count == null ? 0 : count;
    }

    public Map<Long, String> findLocalizedNames(Collection<Long> osmIds, Locale locale) {
        if (osmIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        String localeKey = "name:" + locale.getLanguage();
        List<Long> ids = new ArrayList<>(osmIds);
        for (int offset = 0; offset < ids.size(); offset += NAME_BATCH_SIZE) {
            List<Long> batch = ids.subList(offset, Math.min(offset + NAME_BATCH_SIZE, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String sql = """
                    SELECT osm_id,
                           COALESCE(all_names ->> ?, all_names ->> 'name', 'Area #' || osm_id) AS localized_name
                    FROM osm_names
                    WHERE osm_type = 'R' AND osm_id IN (%s)
                    """.formatted(placeholders);
            List<Object> args = new ArrayList<>(batch.size() + 1);
            args.add(localeKey);
            args.addAll(batch);
            jdbcTemplate.query(sql, rs -> {
                result.put(rs.getLong("osm_id"), rs.getString("localized_name"));
            }, args.toArray());
        }
        return result;
    }


    public record RegionSearchHit(long osmId, String label) {
    }

    /**
     * Searches all name values of a region (including alternate names) for the given query.
     * Only regions that are part of the coverage hierarchy are returned.
     */
    public List<RegionSearchHit> search(String query, Locale locale, int limit) {
        if (query == null) {
            return List.of();
        }
        String trimmed = query.trim();
        if (trimmed.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        if (trimmed.length() > MAX_SEARCH_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEARCH_QUERY_LENGTH);
        }
        String localeKey = "name:" + locale.getLanguage();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String contains = "%" + escapeLike(lower) + "%";
        String prefix = escapeLike(lower) + "%";

        String sql = """
                SELECT r.osm_id,
                       COALESCE(n.all_names ->> ?, n.all_names ->> 'name') AS label
                FROM osm_names n
                JOIN coverage_region r ON r.osm_id = n.osm_id
                WHERE n.osm_type = 'R'
                  AND COALESCE(n.all_names ->> ?, n.all_names ->> 'name') IS NOT NULL
                  AND lower(n.all_names::text) LIKE ? ESCAPE '\\'
                ORDER BY (lower(COALESCE(n.all_names ->> ?, n.all_names ->> 'name')) LIKE ? ESCAPE '\\') DESC,
                         length(COALESCE(n.all_names ->> ?, n.all_names ->> 'name')),
                         label
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RegionSearchHit(rs.getLong("osm_id"), rs.getString("label")),
                localeKey, localeKey, contains, localeKey, prefix, localeKey, Math.max(1, Math.min(limit, 50)));
    }

    /**
     * Returns the path from the root (continent) down to the given region.
     */
    public List<Long> findPathTo(long osmId) {
        LinkedList<Long> path = new LinkedList<>();
        Long current = osmId;
        for (int depth = 0; depth < MAX_PATH_DEPTH && current != null; depth++) {
            Optional<CoverageRegion> region = findById(current);
            if (region.isEmpty()) {
                break;
            }
            path.addFirst(region.get().osmId());
            current = region.get().parentOsmId();
        }
        return path;
    }

    public Optional<CoverageRegion> findParent(long osmId) {
        return findById(osmId).map(CoverageRegion::parentOsmId).flatMap(this::findById);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
