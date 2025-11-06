package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SignificantPlaceOverrideJdbcService {
    private final JdbcTemplate jdbcTemplate;
    private final PointReaderWriter pointReaderWriter;

    public SignificantPlaceOverrideJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointReaderWriter = pointReaderWriter;
    }

    public Optional<PlaceInformationOverride> findByUserAndPoint(User user, GeoPoint point) {
        double meterInDegrees = GeoUtils.metersToDegreesAtPosition(5.0, point.latitude())[0];
        String sql = "SELECT name, category, timezone FROM significant_places_overrides WHERE user_id = ? AND ST_DWithin(geom, ST_GeomFromText(?, '4326'), ?) ORDER BY ST_Distance(geom, ST_GeomFromText(?, '4326')) ASC LIMIT 1";
        List<PlaceInformationOverride> override = jdbcTemplate.query(sql, (rs, rowNum) -> new PlaceInformationOverride(
                rs.getString("name"),
                SignificantPlace.PlaceType.valueOf(rs.getString("category")),
                java.time.ZoneId.of(rs.getString("timezone"))
        ), user.getId(), pointReaderWriter.write(point), meterInDegrees, pointReaderWriter.write(point));
        return override.stream().findFirst();
    }

    public Optional<PlaceInformationOverride> findByUserAndPoint(User user, SignificantPlace place) {
        return findByUserAndPoint(user, new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid()));
    }

    public void insertOverride(User user, SignificantPlace place) {
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        double meterInDegrees = GeoUtils.metersToDegreesAtPosition(5.0, place.getLatitudeCentroid())[0];
        this.jdbcTemplate.update("DELETE FROM significant_places_overrides WHERE user_id = ? AND ST_DWithin(geom, ST_GeomFromText(?, '4326'), ?)", user.getId(), pointReaderWriter.write(point), meterInDegrees);
        String sql = "INSERT INTO significant_places_overrides (user_id, geom, name, category, timezone) VALUES (?, ST_GeomFromText(?, '4326'), ?, ?, ?)";
        jdbcTemplate.update(sql, user.getId(), pointReaderWriter.write(point), place.getName(), place.getType().name(), place.getTimezone().getId());
    }

    public void clear(User user, SignificantPlace place) {
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        this.jdbcTemplate.update("DELETE FROM significant_places_overrides WHERE user_id = ? AND ST_Equals(geom, ST_GeomFromText(?, '4326'))", user.getId(), pointReaderWriter.write(point));
    }
}
