package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * A batch replaces the visits and trips of its time range. These tests make the run fail after the old visits
 * were already deleted (a trigger rejects the trip inserts of the test user) and check that nothing of the
 * failed run is visible afterwards.
 */
@IntegrationTest
class ProcessingPipelineAtomicityTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final double STAY1_LAT = 53.551086;
    private static final double STAY1_LON = 9.993682;
    private static final double STAY2_LAT = 54.051086;
    private static final double STAY2_LON = 10.493682;

    @Autowired
    private ProcessingPipelineTask processingPipelineTask;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JobSchedulingService jobSchedulingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @AfterEach
    void tearDown() {
        allowTripInserts();
    }

    @Test
    void failingBatchKeepsTheExistingVisitsAndTrips() {
        seedTwoStaysWithMovement();
        runPipeline();

        List<Long> visitIds = visitIds();
        List<Long> tripIds = tripIds();
        assertEquals(2, visitIds.size());
        assertEquals(1, tripIds.size());

        // the range gets reprocessed, but the run fails after the old visits and trips were already deleted
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, T0, T0.plus(5, ChronoUnit.HOURS));
        rejectTripInserts();
        runPipeline();

        assertEquals(visitIds, visitIds(), "the visits must survive a failed run");
        assertEquals(tripIds, tripIds(), "the trips must survive a failed run");
        assertTrue(rawLocationPointJdbcService.countUnprocessedByUser(user) > 0, "the failed batch must stay unprocessed");

        allowTripInserts();
        runPipeline();

        assertEquals(2, visitIds().size());
        assertEquals(1, tripIds().size());
        assertEquals(0, rawLocationPointJdbcService.countUnprocessedByUser(user));
    }

    @Test
    void geocodingIsOnlyEnqueuedForCommittedPlaces() {
        // for every geocoding job: is its place visible to other connections at the time it is enqueued?
        List<Boolean> placeVisibleAtEnqueue = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            if (invocation.getArgument(1) instanceof ReverseGeocodingListener.TaskData task && task.username().equals(user.getUsername())) {
                placeVisibleAtEnqueue.add(CompletableFuture.supplyAsync(() -> jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM significant_places WHERE id = ?", Long.class, task.placeId()) == 1).join());
            }
            return null;
        }).when(jobSchedulingService).enqueueTask(any(), any(), any());

        seedTwoStaysWithMovement();
        rejectTripInserts();
        runPipeline();

        assertEquals(0, placeCount(), "the places of a failed run must be rolled back");
        assertTrue(placeVisibleAtEnqueue.isEmpty(), "rolled back places must not be geocoded");

        allowTripInserts();
        runPipeline();

        assertEquals(2, placeCount());
        assertEquals(List.of(true, true), placeVisibleAtEnqueue);
    }

    private void runPipeline() {
        processingPipelineTask.execute(new ProcessingPipelineTask.TaskData(user.getUsername(), null, null));
    }

    private void rejectTripInserts() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION test_reject_trip_insert() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'trip insert rejected by test';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("CREATE TRIGGER " + triggerName() + " BEFORE INSERT ON trips FOR EACH ROW WHEN (NEW.user_id = "
                + user.getId() + ") EXECUTE FUNCTION test_reject_trip_insert()");
    }

    private void allowTripInserts() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName() + " ON trips");
    }

    private String triggerName() {
        return "test_reject_trip_insert_" + user.getId();
    }

    private List<Long> visitIds() {
        return jdbcTemplate.queryForList("SELECT id FROM processed_visits WHERE user_id = ? ORDER BY id", Long.class, user.getId());
    }

    private List<Long> tripIds() {
        return jdbcTemplate.queryForList("SELECT id FROM trips WHERE user_id = ? ORDER BY id", Long.class, user.getId());
    }

    private long placeCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM significant_places WHERE user_id = ?", Long.class, user.getId());
    }

    private void seedTwoStaysWithMovement() {
        List<LocationPoint> points = new ArrayList<>();
        seedStayAt(points, STAY1_LAT, STAY1_LON, T0, 2);
        seedMovement(points, STAY1_LAT, STAY1_LON, STAY2_LAT, STAY2_LON, T0.plus(2, ChronoUnit.HOURS), 1);
        seedStayAt(points, STAY2_LAT, STAY2_LON, T0.plus(3, ChronoUnit.HOURS), 2);
        TimeRange range = TimeRange.of(T0, T0.plus(5, ChronoUnit.HOURS));
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
    }

    private void seedStayAt(List<LocationPoint> points, double latitude, double longitude, Instant start, int hours) {
        for (int i = 0; i < hours * 240; i++) {
            points.add(point(latitude, longitude, start.plus(i * 15L, ChronoUnit.SECONDS)));
        }
    }

    private void seedMovement(List<LocationPoint> points, double fromLat, double fromLon, double toLat, double toLon, Instant start, int hours) {
        int steps = hours * 240;
        for (int i = 0; i < steps; i++) {
            double fraction = (double) i / steps;
            points.add(point(fromLat + (toLat - fromLat) * fraction,
                    fromLon + (toLon - fromLon) * fraction,
                    start.plus(i * 15L, ChronoUnit.SECONDS)));
        }
    }

    private LocationPoint point(double latitude, double longitude, Instant timestamp) {
        LocationPoint point = new LocationPoint();
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setTimestamp(timestamp);
        point.setAccuracyMeters(10.0);
        return point;
    }
}
