package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@IntegrationTest
class DataGapProcessingTest {

    private static final Instant FIRST_POINT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant LAST_POINT_BEFORE_OUTAGE = Instant.parse("2026-08-25T10:08:00Z");
    private static final Instant RESUME = Instant.parse("2026-08-25T14:00:00Z");
    private static final Instant LAST_POINT = Instant.parse("2026-08-25T14:08:00Z");

    @Autowired
    private LocationDataCleanupTask locationDataCleanupTask;
    @Autowired
    private UpdateCuratedTimelineTask updateCuratedTimelineTask;
    @Autowired
    private ProcessingPipelineTask processingPipelineTask;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JobSchedulingService jobSchedulingService;

    @Test
    void resumedDataBridgesTheOutageGapAndYieldsOneVisitAcrossIt() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);

        // 3 points before the outage and 3 after the resume, all at the same place so the
        // gap is interpolatable (max interpolation distance 50m)
        List<LocationPoint> points = new ArrayList<>();
        points.addAll(pointsAt(FIRST_POINT, 3));
        points.addAll(pointsAt(RESUME, 3));
        assertEquals(6, sourceLocationPointJdbcService.bulkInsert(user, device, points));

        // the resume flush [14:00, 14:08] is promoted and cleaned up
        locationDataCleanupTask.execute(new LocationDataCleanupTask.TaskData(user, device, RESUME, LAST_POINT, UUID.randomUUID(), null));

        // real reseed + gap filling, exactly what UpdateCuratedTimelineTask does
        updateCuratedTimelineTask.execute(capturedEnqueueOf(UpdateCuratedTimelineTask.TaskData.class));

        Long syntheticBridges = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM raw_location_points WHERE user_id = ? AND synthetic AND timestamp > ? AND timestamp < ?",
                Long.class, user.getId(), Timestamp.from(LAST_POINT_BEFORE_OUTAGE), Timestamp.from(RESUME));
        assertTrue(syntheticBridges > 500, "expected the outage gap to be bridged with synthetic points, got " + syntheticBridges);

        // the pipeline processes the reseeded and bridged timeline (second enqueue)
        processingPipelineTask.execute(capturedEnqueueOf(ProcessingPipelineTask.TaskData.class));

        Long visits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_visits WHERE user_id = ?", Long.class, user.getId());
        assertEquals(1, visits, "the whole span including the outage should be detected as one visit");

        var visit = jdbcTemplate.queryForMap(
                "SELECT start_time, end_time FROM processed_visits WHERE user_id = ?", user.getId());
        assertEquals(FIRST_POINT, ((Timestamp) visit.get("start_time")).toInstant());
        assertEquals(LAST_POINT, ((Timestamp) visit.get("end_time")).toInstant());
    }

    /**
     * Extracts the single enqueued task of the wanted type from the mocked scheduler,
     * ignoring unrelated enqueues (e.g. the async h3-indexing job during context startup).
     */
    @SuppressWarnings("unchecked")
    private <T extends com.dedicatedcode.reitti.service.JobContext<?>> T capturedEnqueueOf(Class<T> type) {
        ArgumentCaptor<com.dedicatedcode.reitti.service.JobContext> captor = ArgumentCaptor.forClass(com.dedicatedcode.reitti.service.JobContext.class);
        verify(jobSchedulingService, atLeast(1)).enqueueTask(any(), captor.capture(), any());
        List<T> matches = captor.getAllValues().stream()
                .filter(value -> value != null && type.isAssignableFrom(value.getClass()))
                .map(value -> (T) value)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one enqueued " + type.getSimpleName() + ", got " + matches.size());
        return matches.getFirst();
    }

    private List<LocationPoint> pointsAt(Instant start, int count) {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocationPoint point = new LocationPoint();
            point.setTimestamp(start.plus(i * 4L, ChronoUnit.MINUTES));
            point.setLatitude(53.551086);
            point.setLongitude(9.993682);
            point.setAccuracyMeters(10.0);
            points.add(point);
        }
        return points;
    }
}
