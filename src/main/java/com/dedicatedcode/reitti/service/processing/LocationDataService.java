package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class LocationDataService {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataService.class);

    private final GeometryFactory geometryFactory;
    private final JdbcTemplate jdbcTemplate;

    public LocationDataService(GeometryFactory geometryFactory, JdbcTemplate jdbcTemplate) {
        this.geometryFactory = geometryFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void processLocationData(User user, List<LocationDataRequest.LocationPoint> points) {
        String sql = "INSERT INTO raw_location_points (user_id , activity_provided, timestamp, accuracy_meters, geom, processed) " +
                "VALUES (?, ?,  ?,  ?, CAST(? AS geometry), false) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationDataRequest.LocationPoint point : points) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[] {
                    user.getId(),
                    point.getActivity(),
                    timestamp,
                    point.getAccuracyMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
