package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class ExcessDensityHandlerTest {

    @Autowired
    private ExcessDensityHandler excessDensityHandler;

    @Autowired
    private SourceLocationPointJdbcService rawLocationPointService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void shouldMarkPointsAsIgnoredWhenTooCloseInTime() {
        // Given: real points very close together (within tolerance)
        Instant base = Instant.parse("2023-01-01T10:00:00Z");
        createAndSaveRawPoint(base, 50.0, 8.0);
        createAndSaveRawPoint(base.plus(5, ChronoUnit.SECONDS), 50.0001, 8.0001);
        createAndSaveRawPoint(base.plus(10, ChronoUnit.SECONDS), 50.0002, 8.0002);

        // When: handle excess
        List<SourceLocationPoint> points = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, null, base.minus(1, ChronoUnit.MINUTES),
                        base.plus(1, ChronoUnit.MINUTES), false, true);
        excessDensityHandler.handleExcess(testUser, null, TimeRange.of(base, base.plus(30, ChronoUnit.SECONDS)));

        // Then: some points should be marked as ignored
        List<SourceLocationPoint> after = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, null, base.minus(1, ChronoUnit.MINUTES),
                        base.plus(1, ChronoUnit.MINUTES), false, true);
        long ignored = after.stream().filter(SourceLocationPoint::isIgnored).count();
        assertEquals(1, ignored, "One point should be ignored (the middle one according to ordering)");
    }

    private SourceLocationPoint createAndSaveRawPoint(Instant timestamp, double lat, double lon) {
        SourceLocationPoint point = new SourceLocationPoint(
                null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, false, false);
        return rawLocationPointService.create(testUser, null, point);
    }

    // Sorter as used by the normalizer (stable ordering)
    private java.util.Comparator<RawLocationPoint> pointComparator() {
        return java.util.Comparator
                .comparing(RawLocationPoint::getTimestamp)
                .thenComparing(p -> p.getGeom().latitude())
                .thenComparing(p -> p.getGeom().longitude())
                .thenComparing(RawLocationPoint::isSynthetic);
    }
}