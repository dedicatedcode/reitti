package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Streams with a chunk size of 2 so every range crosses several keyset cursor boundaries.
 */
@IntegrationTest
@TestPropertySource(properties = "reitti.processing.point-chunk-size=2")
class RawLocationPointStreamIntegrationTest {

    private static final Instant START = Instant.parse("2024-03-01T00:00:00Z");

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void streamByUserAndTimestampBetween_ShouldReturnEveryPointOnceAcrossChunks() {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expected.add(createPoint(testUser, START.plus(i, ChronoUnit.MINUTES)).getId());
        }
        createPoint(testingService.randomUser(), START.plus(3, ChronoUnit.MINUTES));

        RawLocationPointStream stream = rawLocationPointJdbcService.streamByUserAndTimestampBetween(
                testUser, START, START.plus(1, ChronoUnit.HOURS));

        assertEquals(7, stream.getCount());
        assertEquals(expected, ids(stream));
    }

    @Test
    void previewStreamByUserAndTimestampBetween_ShouldNotSkipOrRepeatPointsWithTiedTimestamps() {
        // preview_raw_location_points has no unique (user_id, timestamp) index, so a chunk can end
        // in the middle of a group of points sharing one timestamp and the cursor must fall back to id.
        String previewId = UUID.randomUUID().toString();
        Instant tied = START.plus(1, ChronoUnit.MINUTES);
        List<Long> expected = new ArrayList<>();
        expected.add(createPreviewPoint(testUser, previewId, START));
        for (int i = 0; i < 4; i++) {
            expected.add(createPreviewPoint(testUser, previewId, tied));
        }
        expected.add(createPreviewPoint(testUser, previewId, START.plus(2, ChronoUnit.MINUTES)));

        RawLocationPointStream stream = previewRawLocationPointJdbcService.streamByUserAndTimestampBetween(
                testUser, previewId, START, START.plus(1, ChronoUnit.HOURS));

        assertEquals(6, stream.getCount());
        assertEquals(expected, ids(stream));
    }

    private static List<Long> ids(RawLocationPointStream stream) {
        List<Long> ids = new ArrayList<>();
        for (RawLocationPoint point : stream) {
            ids.add(point.getId());
        }
        return ids;
    }

    private RawLocationPoint createPoint(User user, Instant timestamp) {
        return rawLocationPointJdbcService.create(user, new RawLocationPoint(
                null, null, timestamp, new GeoPoint(53.863149, 10.700927), 10.0, null, false, false, 1L));
    }

    private long createPreviewPoint(User user, String previewId, Instant timestamp) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO preview_raw_location_points (accuracy_meters, timestamp, user_id, geom, preview_id) " +
                        "VALUES (10.0, ?, ?, ST_GeomFromText('POINT(10.700927 53.863149)', 4326), ?) RETURNING id",
                Long.class, Timestamp.from(timestamp), user.getId(), previewId);
    }
}
