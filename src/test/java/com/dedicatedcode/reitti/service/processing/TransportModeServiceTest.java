package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
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
import static org.mockito.Mockito.lenient;
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
        lenient().when(transportModeJdbcService.getTransportModeConfigs(any(User.class))).thenReturn(List.of(
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
    void usesSlowestConfiguredModeForSinglePoint() {
        List<TransportModeSegment> segments = service.segmentTrip(user, List.of(pt(0, 0)), T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.WALKING, 0, 300);
    }

    @Test
    void usesSlowestConfiguredModeForIdenticalTimestamps() {
        List<TransportModeSegment> segments = service.segmentTrip(user,
                List.of(pt(0, 0), pt(0, 10)), T0, T0.plusSeconds(120));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.WALKING, 0, 120);
    }

    @Test
    void derivesSlowestModeFromUserConfiguration() {
        lenient().when(transportModeJdbcService.getTransportModeConfigs(any(User.class))).thenReturn(List.of(
                new TransportModeConfig(TransportMode.DRIVING, 120.0),
                new TransportModeConfig(TransportMode.CYCLING, 20.0),
                new TransportModeConfig(TransportMode.TRANSIT, null)
        ));

        List<TransportModeSegment> segments = service.segmentTrip(user, List.of(pt(0, 0)), T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.CYCLING, 0, 300);
    }

    @Test
    void fallsBackToWalkingWhenNoConfigHasSpeedLimit() {
        lenient().when(transportModeJdbcService.getTransportModeConfigs(any(User.class))).thenReturn(List.of(
                new TransportModeConfig(TransportMode.TRANSIT, null)
        ));

        List<TransportModeSegment> segments = service.segmentTrip(user, List.of(pt(0, 0)), T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.WALKING, 0, 300);
    }

    @Test
    void appliesManualOverrideToWholeMergedSegment() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        when(transportModeOverrideJdbcService.getTransportModeOverrides(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.CYCLING, T0.plusSeconds(270))));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.CYCLING, 0, 300);
    }

    @Test
    void overrideSurvivesFlankedAbsorption() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= 80; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        when(transportModeOverrideJdbcService.getTransportModeOverrides(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.TRANSIT, T0.plusSeconds(600))));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(1200));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.TRANSIT, 0, 1200);
    }

    @Test
    void overridesReplaceOnlyTheirOwnSegmentsAndAdjacentSameModeSegmentsMerge() {
        // First half walking (slow), second half driving (fast) → two merged segments
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        for (int i = 21; i <= 40; i++) {
            points.add(pt(i * 15L, 300.0 + (i - 20) * 250.0));
        }
        when(transportModeOverrideJdbcService.getTransportModeOverrides(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.CYCLING, T0.plusSeconds(100))));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(600));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.CYCLING, 0, 300);
        assertSegment(segments.get(1), TransportMode.DRIVING, 300, 300);
    }

    @Test
    void twoOverridesInDifferentSegmentsReplaceEachSegmentFully() {
        List<RawLocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            points.add(pt(i * 15L, (i + 1) * 15.0));
        }
        for (int i = 21; i <= 40; i++) {
            points.add(pt(i * 15L, 300.0 + (i - 20) * 250.0));
        }
        when(transportModeOverrideJdbcService.getTransportModeOverrides(any(User.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.CYCLING, T0.plusSeconds(100)),
                        new TransportModeOverrideJdbcService.TransportModeOverride(TransportMode.TRANSIT, T0.plusSeconds(400))));

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(600));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.CYCLING, 0, 300);
        assertSegment(segments.get(1), TransportMode.TRANSIT, 300, 300);
    }

    @Test
    void absorbsRedLightAndTrafficJamInsideCommute() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(
                4.8, 4.9, 4.6, 4.8, 38, 42, 35, 0.3, 40, 45, 39, 5.2, 6.1, 7.4, 5.5, 0.2, 41, 38, 4.5, 4.2),
                T0, T0.plusSeconds(1200));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 240);
        assertSegment(segments.get(1), TransportMode.DRIVING, 240, 840);
        assertSegment(segments.get(2), TransportMode.WALKING, 1080, 120);
    }

    @Test
    void keepsTrainRideBetweenWalkingRuns() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(5, 5, 5, 150, 150, 150, 150, 150, 5, 5, 5),
                T0, T0.plusSeconds(660));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 180);
        assertSegment(segments.get(1), TransportMode.TRANSIT, 180, 300);
        assertSegment(segments.get(2), TransportMode.WALKING, 480, 180);
    }

    @Test
    void keepsCyclingPeakBetweenWalkingRuns() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(5, 15, 15, 15, 5),
                T0, T0.plusSeconds(300));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 60);
        assertSegment(segments.get(1), TransportMode.CYCLING, 60, 180);
        assertSegment(segments.get(2), TransportMode.WALKING, 240, 60);
    }

    @Test
    void absorbsOneMinuteCyclingRunBetweenWalks() {
        // The accepted counterpart to the valley rule's cost: a peak no longer than one window is
        // treated as a glitch, so a 250 m stretch of cycling inside a walk is lost rather than kept.
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(5, 15, 5),
                T0, T0.plusSeconds(180));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.WALKING, 0, 180);
    }

    @Test
    void leavesBoundaryRunsUnabsorbed() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(40, 40, 40, 40, 40, 40, 40, 5, 5, 5),
                T0, T0.plusSeconds(600));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.DRIVING, 0, 420);
        assertSegment(segments.get(1), TransportMode.WALKING, 420, 180);
    }

    @Test
    void leavesLeadingBoundaryRunUnabsorbed() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(5, 5, 40, 40, 40),
                T0, T0.plusSeconds(300));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 120);
        assertSegment(segments.get(1), TransportMode.DRIVING, 120, 180);
    }

    @Test
    void absorbsInteriorWalkBetweenDrives() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(40, 40, 40, 5, 5, 5, 5, 40, 40, 40),
                T0, T0.plusSeconds(600));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.DRIVING, 0, 600);
    }

    @Test
    void absorbsLongerJamsWithoutDurationBound() {
        // A 10-minute jam between two driving runs, far beyond any former short-segment threshold. The
        // driving runs are two windows each so they are not themselves one-minute peaks between walks.
        List<TransportModeSegment> segments = service.segmentTrip(user,
                trace(5, 5, 40, 40, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 40, 40, 5, 5),
                T0, T0.plusSeconds(1080));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.WALKING, 0, 120);
        assertSegment(segments.get(1), TransportMode.DRIVING, 120, 840);
        assertSegment(segments.get(2), TransportMode.WALKING, 960, 120);
    }

    @Test
    void absorbsSingleWindowSpeedBurstBetweenCyclingRuns() {
        // One minute reading above every configured threshold, flanked by bicycle-speed runs: a
        // localisation glitch rather than a mode change.
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(20, 20, 150, 20, 20),
                T0, T0.plusSeconds(300));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.CYCLING, 0, 300);
    }

    @Test
    void keepsMultiWindowTransitBetweenCyclingRuns() {
        // The genuine bike-train-bike shape: a real ride outlasts a single window and must survive.
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(20, 20, 150, 150, 150, 20, 20),
                T0, T0.plusSeconds(420));

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), TransportMode.CYCLING, 0, 120);
        assertSegment(segments.get(1), TransportMode.TRANSIT, 120, 180);
        assertSegment(segments.get(2), TransportMode.CYCLING, 300, 120);
    }

    @Test
    void keepsSpeedBurstAtTripBoundary() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(150, 20, 20),
                T0, T0.plusSeconds(180));

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), TransportMode.TRANSIT, 0, 60);
        assertSegment(segments.get(1), TransportMode.CYCLING, 60, 120);
    }

    @Test
    void absorbsBurstThenCascadesToValley() {
        // The burst exposes a valley behind it; the fixpoint loop has to fold both away.
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(20, 150, 5, 20),
                T0, T0.plusSeconds(240));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.CYCLING, 0, 240);
    }

    @Test
    void preservesDistanceWhenAbsorbingBurst() {
        List<RawLocationPoint> points = trace(20, 20, 150, 20, 20);

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(300));

        assertEquals(GeoUtils.calculateTripDistance(points), totalDistance(segments), 0.001);
        assertEquals(300, segments.stream().mapToLong(TransportModeSegment::durationSeconds).sum());
    }

    @Test
    void preservesTotalDurationAcrossAbsorption() {
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(
                4.8, 4.9, 4.6, 4.8, 38, 42, 35, 0.3, 40, 45, 39, 5.2, 6.1, 7.4, 5.5, 0.2, 41, 38, 4.5, 4.2),
                T0, T0.plusSeconds(1200));

        assertEquals(1200, segments.stream().mapToLong(TransportModeSegment::durationSeconds).sum());
    }

    @Test
    void preservesDistanceAcrossAbsorption() {
        double[] profile = {4.8, 4.9, 4.6, 4.8, 38, 42, 35, 0.3, 40, 45, 39, 5.2, 6.1, 7.4, 5.5, 0.2, 41, 38, 4.5, 4.2};
        List<RawLocationPoint> points = trace(profile);

        List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(1200));

        assertEquals(GeoUtils.calculateTripDistance(points), totalDistance(segments), 0.001);
    }

    @Test
    void preservesDistanceAtEverySamplingCadence() {
        double[] profile = {4.8, 4.9, 4.6, 4.8, 38, 42, 35, 0.3, 40, 45, 39, 5.2, 6.1, 7.4, 5.5, 0.2, 41, 38, 4.5, 4.2};
        // Each window boundary used to drop one sampling interval of travel, so the loss scaled with
        // cadence: 30s sampling lost roughly half the distance. Pin every cadence.
        for (int cadenceSeconds : new int[]{5, 10, 15, 30, 60}) {
            List<RawLocationPoint> points = trace(profile, cadenceSeconds);

            List<TransportModeSegment> segments = service.segmentTrip(user, points, T0, T0.plusSeconds(1200));

            assertEquals(GeoUtils.calculateTripDistance(points), totalDistance(segments), 0.001,
                    "segment distance must match the trip at " + cadenceSeconds + "s cadence");
        }
    }

    private double totalDistance(List<TransportModeSegment> segments) {
        return segments.stream().mapToDouble(TransportModeSegment::distanceMeters).sum();
    }

    @Test
    void absorbsUsingCustomUserBands() {
        lenient().when(transportModeJdbcService.getTransportModeConfigs(any(User.class))).thenReturn(List.of(
                new TransportModeConfig(TransportMode.CYCLING, 10.0),
                new TransportModeConfig(TransportMode.DRIVING, 120.0)
        ));

        // Walking is not configured, so CYCLING is the slowest band and the walking-speed run is the valley.
        List<TransportModeSegment> segments = service.segmentTrip(user, trace(30, 5, 5, 5, 5, 5, 5, 30),
                T0, T0.plusSeconds(480));

        assertEquals(1, segments.size());
        assertSegment(segments.getFirst(), TransportMode.DRIVING, 0, 480);
    }

    /**
     * Builds a trace with one 60-second window per entry, sampled on the given cadence. The final point
     * sits exactly on the last window boundary, so each window classifies at exactly the requested speed.
     */
    private List<RawLocationPoint> trace(double... kmhPerWindow) {
        return trace(kmhPerWindow, 10);
    }

    private List<RawLocationPoint> trace(double[] kmhPerWindow, int cadenceSeconds) {
        List<RawLocationPoint> points = new ArrayList<>();
        double northMeters = 0;
        int totalSeconds = kmhPerWindow.length * 60;
        for (int offset = 0; offset <= totalSeconds; offset += cadenceSeconds) {
            points.add(pt(offset, northMeters));
            if (offset < totalSeconds) {
                int window = Math.min(offset / 60, kmhPerWindow.length - 1);
                northMeters += kmhPerWindow[window] * cadenceSeconds / 3.6;
            }
        }
        return points;
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
