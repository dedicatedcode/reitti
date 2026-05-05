package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
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
    private RawLocationPointJdbcService rawLocationPointService;

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
        List<RawLocationPoint> points = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, base.minus(1, ChronoUnit.MINUTES),
                        base.plus(1, ChronoUnit.MINUTES));
        excessDensityHandler.handleExcess(testUser, null, TimeRange.of(base, base.plus(30, ChronoUnit.SECONDS)));

        // Then: some points should be marked as ignored
        List<RawLocationPoint> after = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, base.minus(1, ChronoUnit.MINUTES),
                        base.plus(1, ChronoUnit.MINUTES));
        long ignored = after.stream().filter(RawLocationPoint::isIgnored).count();
        assertEquals(1, ignored, "One point should be ignored (the middle one according to ordering)");
    }

    private RawLocationPoint createAndSaveRawPoint(Instant timestamp, double lat, double lon) {
        RawLocationPoint point = new RawLocationPoint(
                null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, false, false, false, false, 1L
        );
        return rawLocationPointService.create(testUser, point);
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