package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class ProcessingWindowResolverTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final Duration STEP = Duration.ofMinutes(5);

    @Autowired
    private ProcessingWindowResolver resolver;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private TestingService testingService;

    @Test
    void continuousStreamingGetsAContextMarginInsteadOfDays() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        // 100 points, 5 minutes apart: T0 .. T0+495min
        insert(user, device, T0, 100);

        // the last flush of the stream is promoted
        TimeRange window = resolver.resolve(user, device, TimeRange.of(T0.plus(490, java.time.temporal.ChronoUnit.MINUTES), T0.plus(495, java.time.temporal.ChronoUnit.MINUTES)));

        // 50 points of context backwards: index 48 .. 97 -> oldest at T0+240min
        assertEquals(T0.plus(240, java.time.temporal.ChronoUnit.MINUTES), window.start());
        // nothing follows the promoted range, but the exclusive end must still cover the last point
        assertEquals(T0.plus(495, java.time.temporal.ChronoUnit.MINUTES).plusMillis(1), window.end());
    }

    @Test
    void closedGapIsBridgedByReachingBackToThePreOutagePoint() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        // phone reports until 10:04, then is offline for ~4h, resumes at 14:00
        insert(user, device, Instant.parse("2026-08-25T10:00:00Z"), 3);
        Instant resume = Instant.parse("2026-08-25T14:00:00Z");
        insert(user, device, resume, 2);

        TimeRange window = resolver.resolve(user, device, TimeRange.of(resume, resume.plus(STEP)));

        // the window reaches back over the whole gap to the last pre-outage point,
        // so the synthetic interpolation sees both gap endpoints
        assertEquals(Instant.parse("2026-08-25T10:00:00Z"), window.start());
        assertEquals(resume.plus(STEP).plusMillis(1), window.end());
    }

    @Test
    void firstPromotionHasNoBackwardExtension() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        insert(user, device, T0, 100);

        TimeRange window = resolver.resolve(user, device, TimeRange.of(T0, T0.plus(STEP)));

        assertEquals(T0, window.start());
        // backfill: 50 points follow the promoted range -> T0+10min .. T0+255min
        assertEquals(T0.plus(255, java.time.temporal.ChronoUnit.MINUTES), window.end());
    }

    @Test
    void sparseDataIsCappedAtMaxInterpolationGap() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        insert(user, device, Instant.parse("2026-08-23T10:00:00Z"), 1);
        Instant resume = Instant.parse("2026-08-24T11:00:00Z"); // 25h after the single pre-outage point
        insert(user, device, resume, 2);

        TimeRange window = resolver.resolve(user, device, TimeRange.of(resume, resume.plus(STEP)));

        // the context point lies 25h back; the widening is capped at maxInterpolationGapMinutes (24h)
        // so a longer outage is never partially bridged
        assertEquals(resume.minus(Duration.ofHours(24)), window.start());
    }

    @Test
    void noPriorPointsKeepsThePromotedStart() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        TimeRange window = resolver.resolve(user, device, TimeRange.of(T0, T0.plus(STEP)));

        assertEquals(T0, window.start());
        assertEquals(T0.plus(STEP).plusMillis(1), window.end());
    }

    private void insert(User user, Device device, Instant start, int count) {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocationPoint point = new LocationPoint();
            point.setTimestamp(start.plus(STEP.multipliedBy(i)));
            point.setLatitude(53.551086);
            point.setLongitude(9.993682);
            point.setAccuracyMeters(10.0);
            points.add(point);
        }
        int inserted = sourceLocationPointJdbcService.bulkInsert(user, device, points);
        assertTrue(inserted == count, "expected all points to be inserted");
    }
}
