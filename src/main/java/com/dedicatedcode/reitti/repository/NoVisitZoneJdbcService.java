package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NoVisitZoneJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PointReaderWriter pointReaderWriter;
    private final RowMapper<NoVisitZone> rowMapper;

    public NoVisitZoneJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointReaderWriter = pointReaderWriter;
        this.rowMapper = (rs, _) -> new NoVisitZone(
                rs.getLong("id"),
                rs.getString("name"),
                pointReaderWriter.wktToPolygon(rs.getString("geom")),
                rs.getTimestamp("created_at").toInstant());
    }

    public NoVisitZone create(User user, NoVisitZone zone) {
        String sql = "INSERT INTO no_visit_zones (user_id, name, geom) VALUES (?, ?, ST_GeomFromText(?, '4326')) RETURNING id";
        String polygonWkt = this.pointReaderWriter.polygonToWkt(zone.polygon());
        Long id = jdbcTemplate.queryForObject(sql, Long.class, user.getId(), zone.name(), polygonWkt);
        return findById(user, id).orElseThrow();
    }

    public Optional<NoVisitZone> findById(User user, Long id) {
        String sql = "SELECT id, name, ST_AsText(geom) as geom, created_at FROM no_visit_zones WHERE user_id = ? AND id = ?";
        List<NoVisitZone> result = jdbcTemplate.query(sql, rowMapper, user.getId(), id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public List<NoVisitZone> findByUser(User user) {
        String sql = "SELECT id, name, ST_AsText(geom) as geom, created_at FROM no_visit_zones WHERE user_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper, user.getId());
    }

    public void delete(User user, Long id) {
        jdbcTemplate.update("DELETE FROM no_visit_zones WHERE user_id = ? AND id = ?", user.getId(), id);
    }
}
