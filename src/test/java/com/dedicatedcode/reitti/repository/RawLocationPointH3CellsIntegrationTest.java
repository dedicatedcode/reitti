package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.controller.api.v2.CoverageController;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@IntegrationTest
class RawLocationPointH3CellsIntegrationTest {

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SpatialCoverageService spatialCoverageService;

    @Test
    void readsVisitedCellsFromTheSourceStreamEvenWhenRlpH3IsNotPopulated() {
        testingService.clearData();
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
        long h3Cell = spatialCoverageService.getLevelCellForPoint(53.55, 9.99, 12);

        jdbcTemplate.update("""
                        INSERT INTO raw_source_points
                            (user_id, device_id, timestamp, accuracy_meters, geom, status, invalid, h3_cell)
                        VALUES (?, ?, ?, 10.0, ST_GeomFromText('POINT(9.99 53.55)', 4326), 0, false, ?)
                        """,
                user.getId(), device.id(), Timestamp.from(timestamp), h3Cell);

        List<CoverageController.H3CellCount> cells = rawLocationPointJdbcService.findVisitedH3CellsCounts(
                user, timestamp.minus(1, ChronoUnit.HOURS), timestamp.plus(1, ChronoUnit.HOURS));

        assertFalse(cells.isEmpty(), "coverage cells must come from v_source_stream");
        assertEquals(Long.toString(h3Cell), cells.getFirst().hexagon());
        assertEquals(1, cells.getFirst().count());
    }
}
