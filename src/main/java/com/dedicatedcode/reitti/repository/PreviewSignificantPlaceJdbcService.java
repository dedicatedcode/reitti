package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.locationtech.jts.geom.Point;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PreviewSignificantPlaceJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PointReaderWriter  pointReaderWriter;

    public PreviewSignificantPlaceJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointReaderWriter = pointReaderWriter;
    }

    private final RowMapper<SignificantPlace> significantPlaceRowMapper = (rs, _) -> new SignificantPlace(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("city"),
            rs.getString("country_code"),
            rs.getDouble("latitude_centroid"),
            rs.getDouble("longitude_centroid"),
            SignificantPlace.PlaceType.valueOf(rs.getString("type")),
            rs.getString("timezone") != null ? ZoneId.of(rs.getString("timezone")) : null,
            rs.getBoolean("geocoded"),
            rs.getLong("version"));

    public List<SignificantPlace> findNearbyPlaces(Long userId, Point point, double distanceInMeters, String previewId) {
        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.city, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.timezone, sp.geocoded, sp.version " +
                "FROM preview_significant_places sp " +
                "WHERE sp.user_id = ? AND preview_id = ? " +
                "AND ST_DWithin(sp.geom, ST_GeomFromText(?, '4326'), ?)";
        return jdbcTemplate.query(sql, significantPlaceRowMapper,
                userId, previewId, point.toString(), distanceInMeters);
    }

    public SignificantPlace create(User user, String previewId, SignificantPlace place) {
        String sql = "INSERT INTO preview_significant_places (user_id, preview_id, name, latitude_centroid, longitude_centroid, timezone, geom) " +
                "VALUES (?, ?, ?, ?, ?, ?, ST_GeomFromText(?, '4326')) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                previewId,
                place.getName(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                place.getTimezone().getId(),
                this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid())
        );
        return findById(id).orElseThrow();
    }

    public Optional<SignificantPlace> findById(Long id) {
        String sql = "SELECT sp.id, sp.address, sp.city, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.timezone, sp.geocoded, sp.version " +
                "FROM preview_significant_places sp " +
                "WHERE sp.id = ?";
        List<SignificantPlace> results = jdbcTemplate.query(sql, significantPlaceRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }


    public SignificantPlace update(SignificantPlace place) {
        String sql = "UPDATE preview_significant_places SET name = ?, address = ?, city = ?, country_code = ?, type = ?, latitude_centroid = ?, longitude_centroid = ?, geom = ST_GeomFromText(?, '4326'), timezone = ?, geocoded = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                place.getName(),
                place.getAddress(),
                place.getCity(),
                place.getCountryCode(),
                place.getType().name(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid()),
                place.getTimezone() != null ? place.getTimezone().getId() : null,
                place.isGeocoded(),
                place.getId()
        );
        return findById(place.getId()).orElseThrow();
    }

    public void deleteForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM preview_significant_places WHERE user_id = ?", user.getId());
    }

    public void deleteForPreviewId(String previewId) {
        this.jdbcTemplate.update("DELETE FROM preview_significant_places WHERE preview_id = ?", previewId);
    }
}
