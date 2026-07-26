package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class H3CellUpdateJobTest {
    @Autowired
    private TestingService testingService;

    @Autowired
    private RocksDBH3Service rocksDBH3Service;

    @Autowired
    private SpatialCoverageService spatialCoverageService;

    @Test
    void shouldHandlePromotion() {

        Awaitility.await().atMost(60, TimeUnit.SECONDS)
                .until(() -> rocksDBH3Service.isAvailable());

        User user = this.testingService.randomUser();
        Device device = this.testingService.findDefaultDevice(user);
        this.testingService.importData(user, "/data/gpx/20250617.gpx");
        this.testingService.awaitDataImport(30);

        Optional<CoverageInformation> luebeck = spatialCoverageService.getCoverageInformation(user, device, 27027, Locale.GERMAN);
        Optional<CoverageInformation> innenstadt = spatialCoverageService.getCoverageInformation(user, device, 367855, Locale.GERMAN);
        Optional<CoverageInformation> sanktJuergen = spatialCoverageService.getCoverageInformation(user, device, 367868, Locale.GERMAN);

        assertTrue(luebeck.isPresent());
        assertFalse(innenstadt.isPresent());
        assertTrue(sanktJuergen.isPresent());


    }
}