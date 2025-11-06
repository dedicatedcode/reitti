package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        String sql = "SELECT name, category, timezone FROM significant_places_overrides WHERE user_id = ? AND ST_Equals(geom, ST_GeomFromText(?, '4326'))";
        try {
            PlaceInformationOverride override = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PlaceInformationOverride(
                            rs.getString("name"),
                            SignificantPlace.PlaceType.valueOf(rs.getString("category")),
                            java.time.ZoneId.of(rs.getString("timezone"))
                    ), user.getId(), pointReaderWriter.write(point));
            return Optional.ofNullable(override);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void insertOverride(User user, SignificantPlace place) {
        String sql = "INSERT INTO significant_places_overrides (user_id, geom, name, category, timezone) VALUES (?, ST_GeomFromText(?, '4326'), ?, ?, ?)";
        jdbcTemplate.update(sql, user.getId(), pointReaderWriter.write(place.getLatitudeCentroid(), place.getLongitudeCentroid()), place.getName(), place.getType().name(), place.getTimezone().getId());
    }

    public void clear(User user, SignificantPlace place) {
        this.jdbcTemplate.update("DELETE FROM significant_places_overrides WHERE user_id = ? AND ST_Equals(geom, ST_GeomFromText(?, '4326'))", user.getId(), pointReaderWriter.write(place.getLatitudeCentroid(), place.getLongitudeCentroid()));
    }
}
