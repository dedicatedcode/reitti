package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class PipelineVisitDetectionScopeTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");

    @Autowired
    private UnifiedLocationProcessingService processingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void visitDetectionStaysWithinTheMarginAroundTheBatch() {
        User user = testingService.randomUser();

        // 4 hours of points at one place, 15s apart
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 4 * 240; i++) {
            LocationPoint point = new LocationPoint();
            point.setTimestamp(T0.plus(i * 15L, ChronoUnit.SECONDS));
            point.setLatitude(53.551086);
            point.setLongitude(9.993682);
            point.setAccuracyMeters(10.0);
            points.add(point);
        }
        assertEquals(960, sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points));

        // populate the timeline table from the source points, like UpdateCuratedTimelineTask does
        rawLocationPointJdbcService.dropForReSeeding(user, TimeRange.of(T0, T0.plus(4, ChronoUnit.HOURS)));
        rawLocationPointJdbcService.updateFromDevices(user, TimeRange.of(T0, T0.plus(4, ChronoUnit.HOURS)));

        // the pipeline batch only covers one minute in the middle of the stay
        Instant batchStart = T0.plus(2, ChronoUnit.HOURS);
        processingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), batchStart, batchStart.plus(1, ChronoUnit.MINUTES), null, null, null));

        var visit = jdbcTemplate.queryForMap("SELECT start_time, end_time FROM processed_visits WHERE user_id = ?", user.getId());
        Instant start = ((java.sql.Timestamp) visit.get("start_time")).toInstant();
        Instant end = ((java.sql.Timestamp) visit.get("end_time")).toInstant();

        // margin = minStay (300s) + 2 * maxMerge (300s) = 15min: the detection window is
        // [earliest - 15min, latest + 15min], not ±1 day around the batch
        assertTrue(start.isAfter(batchStart.minus(16, ChronoUnit.MINUTES)),
                "visit detection must not reach days before the batch, but started at " + start);
        assertTrue(end.isBefore(batchStart.plus(17, ChronoUnit.MINUTES)),
                "visit detection must not reach days after the batch, but ended at " + end);
    }
}
