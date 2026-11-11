package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.CoverageRegion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

}
