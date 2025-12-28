package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
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
        double meterInDegrees = GeoUtils.metersToDegreesAtPosition(5.0, point.latitude());
        String sql = """
                SELECT name, category, timezone, ST_AsText(polygon) as polygon FROM significant_places_overrides
                                                WHERE user_id = ?
                                                  AND ST_DWithin(
                                                        COALESCE(polygon, ST_Buffer(geom, ?)),
                                                        ST_GeomFromText(?, '4326'),
                                                        0
                                                    )
                                                ORDER BY ST_Distance(geom, ST_GeomFromText(?, '4326')) LIMIT 1
                """;
        String pointWkt = pointReaderWriter.write(point);
        List<PlaceInformationOverride> override = jdbcTemplate.query(sql, (rs, rowNum) -> new PlaceInformationOverride(
                rs.getString("name"),
                SignificantPlace.PlaceType.valueOf(rs.getString("category")),
                ZoneId.of(rs.getString("timezone")),
                pointReaderWriter.wktToPolygon(rs.getString("polygon"))
        ), user.getId(), meterInDegrees, pointWkt, pointWkt);
        return override.stream().findFirst();
    }

    public Optional<PlaceInformationOverride> findByUserAndPoint(User user, SignificantPlace place) {
        return findByUserAndPoint(user, new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid()));
    }

    public void insertOverride(User user, SignificantPlace place) {
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        double meterInDegrees = GeoUtils.metersToDegreesAtPosition(5.0, place.getLatitudeCentroid());
        this.jdbcTemplate.update("DELETE FROM significant_places_overrides WHERE user_id = ? AND ST_DWithin(geom, ST_GeomFromText(?, '4326'), ?)", user.getId(), pointReaderWriter.write(point), meterInDegrees);

        String polygonWkt = this.pointReaderWriter.polygonToWkt(place.getPolygon());

        String sql = "INSERT INTO significant_places_overrides (user_id, geom, name, category, timezone, polygon) VALUES (?, ST_GeomFromText(?, '4326'), ?, ?, ?, CASE WHEN ?::text IS NOT NULL THEN ST_GeomFromText(?, '4326')  END)";
        jdbcTemplate.update(sql, user.getId(), pointReaderWriter.write(point), place.getName(), place.getType().name(), place.getTimezone().getId(), polygonWkt, polygonWkt);
    }

    public void clear(User user, SignificantPlace place) {
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        this.jdbcTemplate.update("DELETE FROM significant_places_overrides WHERE user_id = ? AND ST_Equals(geom, ST_GeomFromText(?, '4326'))", user.getId(), pointReaderWriter.write(point));
    }
}
