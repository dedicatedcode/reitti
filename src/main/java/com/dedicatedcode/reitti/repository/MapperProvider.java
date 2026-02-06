package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;

import java.time.ZoneId;

@Configuration
public class MapperProvider
{

    private final PointReaderWriter pointReaderWriter;

    public MapperProvider(PointReaderWriter pointReaderWriter) {this.pointReaderWriter = pointReaderWriter;}

    @Bean
    public RowMapper<RawLocationPoint> rawLocationPointRowMapper()
    {
        return (rs, _) -> new RawLocationPoint(
            rs.getLong("id"),
            rs.getTimestamp("timestamp").toInstant(),
            pointReaderWriter.read(rs.getString("geom")),
            rs.getDouble("accuracy_meters"),
            rs.getObject("elevation_meters", Double.class),
            rs.getBoolean("processed"),
            rs.getBoolean("synthetic"),
            rs.getBoolean("ignored"),
            rs.getBoolean("invalid"),
            rs.getLong("version"));
    }
    @Bean
    public RowMapper<SignificantPlace> significantPlaceRowMapper()
    {
        return (rs, _) -> new SignificantPlace(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("city"),
            rs.getString("country_code"),
            rs.getDouble("latitude_centroid"),
            rs.getDouble("longitude_centroid"),
            this.pointReaderWriter.wktToPolygon(rs.getString("polygon")),
            SignificantPlace.PlaceType.valueOf(rs.getString("type")),
            rs.getString("timezone") != null ? ZoneId.of(rs.getString("timezone")) : null,
            rs.getBoolean("geocoded"),
            rs.getLong("version"));
    }
}
