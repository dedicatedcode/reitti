package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportModeServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T10:00:00Z");
    private static final double BASE_LAT = 52.5;
    private static final double LONGITUDE = 13.4;
    private static final double METERS_TO_DEGREES = 1.0 / 111194.93;

    @Mock
    private TransportModeJdbcService transportModeJdbcService;
    @Mock
    private TransportModeOverrideJdbcService transportModeOverrideJdbcService;
    @InjectMocks
    private TransportModeService service;

    private final User user = new User("tester", "Tester");

    @BeforeEach
    void setUp() {
        when(transportModeJdbcService.getTransportModeConfigs(any(User.class))).thenReturn(List.of(
                new TransportModeConfig(TransportMode.WALKING, 7.0),
                new TransportModeConfig(TransportMode.CYCLING, 20.0),
                new TransportModeConfig(TransportMode.DRIVING, 120.0),
                new TransportModeConfig(TransportMode.TRANSIT, null)
        ));
    }

    @Test
    void classifiesSparseTripWithOnlyTwoPointsInsteadOfUnknown() {
        RawLocationPoint start = pt(0, 0);
        RawLocationPoint end = pt(600, 5000);

        List<TransportModeSegment> segments = service.segmentTrip(user, List.of(start, end), T0, T0.plusSeconds(600));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.DRIVING, 0, 600);
    }

    @Test
    void classifiesInteriorSparseSpanWithItsOwnPoints() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        points.add(pt(300, 1195));
        points.add(pt(540, 3195));
        for (int i = 0; i < 13; i++) {
            points.add(pt(675 + i * 15L, 3210 + i * 15.0));
        }

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(840));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 180);
        assertSegment(segments.get(1), TransportMode.DRIVING, 180, 480);
        assertSegment(segments.get(2), TransportMode.WALKING, 660, 180);
    }

    @Test
    void dropsTrailingSparseSpanWithSinglePoint() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        points.add(pt(590, 210));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(590));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.WALKING, 0, 180);
    }

    @Test
    void keepsDenseDrivingTripAsSingleSegment() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            points.add(pt(i * 10L, (i + 1) * 250.0));
        }

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(600));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.DRIVING, 0, 600);
    }

    @Test
    void returnsUnknownForSinglePoint() {
        List<TransportModeSegment> segments = service.segmentTrip(user, List.of(pt(0, 0)), T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.UNKNOWN, 0, 300);
    }

    @Test
    void returnsUnknownForIdenticalTimestamps() {
        List<TransportModeSegment> segments = service.segmentTrip(user,
                List.of(pt(0, 0), pt(0, 10)), T0, T0.plusSeconds(120));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.UNKNOWN, 0, 120);
    }

    @Test
    void appliesManualOverrideToMatchedChunk() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        when(transportModeOverrideJdbcService.getTransportModeOverrides(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.CYCLING, T0.plusSeconds(270))));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(300));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 240);
        assertSegment(segments.get(1), TransportMode.CYCLING, 240, 60);
    }

    private RawLocationPoint pt(long offsetSeconds, double northMeters) {
        return new RawLocationPoint(T0.plusSeconds(offsetSeconds),
                new GeoPoint(BASE_LAT + northMeters * METERS_TO_DEGREES, LONGITUDE), 10.0);
    }

    private void assertSegment(TransportModeSegment segment, TransportMode mode, long offsetSeconds, long durationSeconds) {
        assertEquals(mode, segment.mode());
        assertEquals(offsetSeconds, segment.offsetSeconds());
        assertEquals(durationSeconds, segment.durationSeconds());
    }
}
