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

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SignificantPlaceJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PointReaderWriter  pointReaderWriter;

    public SignificantPlaceJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointReaderWriter = pointReaderWriter;
        this.significantPlaceRowMapper = (rs, _) -> new SignificantPlace(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("country_code"),
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                SignificantPlace.PlaceType.valueOf(rs.getString("type")),
                rs.getBoolean("geocoded"),
                rs.getLong("version"));
    }

    private final RowMapper<SignificantPlace> significantPlaceRowMapper;

    public Page<SignificantPlace> findByUser(User user, PageRequest pageable) {
        String countSql = "SELECT COUNT(*) FROM significant_places WHERE user_id = ?";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, user.getId());

        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.geocoded, sp.version" +
                " FROM significant_places sp " +
                "WHERE sp.user_id = ? ORDER BY sp.id " +
                "LIMIT ? OFFSET ? ";
        List<SignificantPlace> content = jdbcTemplate.query(sql, significantPlaceRowMapper,
                user.getId(), pageable.getPageSize(), pageable.getOffset());

        return new Page<>(content, pageable, total != null ? total : 0);
    }

    public List<SignificantPlace> findNearbyPlaces(Long userId, Point point, double distanceInMeters) {
        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.geocoded, sp.version " +
                "FROM significant_places sp " +
                "WHERE sp.user_id = ? " +
                "AND ST_DWithin(sp.geom, ST_GeomFromText(?, '4326'), ?)";
        return jdbcTemplate.query(sql, significantPlaceRowMapper,
                userId, point.toString(), distanceInMeters);
    }

    public SignificantPlace create(User user, SignificantPlace place) {
        String sql = "INSERT INTO significant_places (user_id, name, latitude_centroid, longitude_centroid, geom) " +
                "VALUES (?, ?, ?, ?, ST_GeomFromText(?, '4326')) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                place.getName(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid())
        );
        return place.withId(id);
    }

    @CacheEvict(cacheNames = "significant-places", key = "#place.id")
    public SignificantPlace update(SignificantPlace place) {
        String sql = "UPDATE significant_places SET name = ?, address = ?, country_code = ?, type = ?, latitude_centroid = ?, longitude_centroid = ?, geom = ST_GeomFromText(?, '4326'), geocoded = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                place.getName(),
                place.getAddress(),
                place.getCountryCode(),
                place.getType().name(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid()),
                place.isGeocoded(),
                place.getId()
        );
        return place;
    }

    @Cacheable("significant-places")
    public Optional<SignificantPlace> findById(Long id) {
        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.geocoded, sp.version " +
                "FROM significant_places sp " +
                "WHERE sp.id = ?";
        List<SignificantPlace> results = jdbcTemplate.query(sql, significantPlaceRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public boolean exists(User user, Long id) {
        return this.jdbcTemplate.queryForObject("SELECT count(*) FROM significant_places WHERE user_id = ? AND id = ?", Integer.class, user.getId(), id) > 0;
    }

    public List<SignificantPlace> findNonGeocodedByUser(User user) {
        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.geocoded, sp.version " +
                "FROM significant_places sp " +
                "WHERE sp.user_id = ? AND sp.geocoded = false " +
                "ORDER BY sp.id";
        return jdbcTemplate.query(sql, significantPlaceRowMapper, user.getId());
    }

    public List<SignificantPlace> findAllByUser(User user) {
        String sql = "SELECT sp.id, sp.address, sp.country_code, sp.type, sp.latitude_centroid, sp.longitude_centroid, sp.name, sp.user_id, ST_AsText(sp.geom) as geom, sp.geocoded, sp.version " +
                "FROM significant_places sp " +
                "WHERE sp.user_id = ? " +
                "ORDER BY sp.id";
        return jdbcTemplate.query(sql, significantPlaceRowMapper, user.getId());
    }
}
