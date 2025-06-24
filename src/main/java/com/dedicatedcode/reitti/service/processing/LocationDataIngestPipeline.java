package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final UserJdbcService userJdbcService;
    private final GeometryFactory geometryFactory;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LocationDataIngestPipeline(UserJdbcService userJdbcService,
                                      GeometryFactory geometryFactory,
                                      JdbcTemplate jdbcTemplate) {
        this.userJdbcService = userJdbcService;
        this.geometryFactory = geometryFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void processLocationData(LocationDataEvent event) {
        long start = System.currentTimeMillis();

        logger.debug("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername());
            return;
        }

        User user = userOpt.get();
        List<LocationDataRequest.LocationPoint> points = event.getPoints();
        String sql = "INSERT INTO raw_location_points (user_id , activity_provided, timestamp, accuracy_meters, geom, processed) " +
                "VALUES (?, ?,  ?,  ?, CAST(? AS geometry), false) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationDataRequest.LocationPoint point : points) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[]{
                    user.getId(),
                    point.getActivity(),
                    timestamp,
                    point.getAccuracyMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
        logger.debug("Finished processing pipeline for user [{}] in [{}]ms", event.getUsername(), System.currentTimeMillis() - start);
    }

}
