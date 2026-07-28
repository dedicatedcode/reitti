package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestJdbcService;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.workbench.DeletedPointDto;
import com.dedicatedcode.reitti.dto.workbench.EditStoreDto;
import com.dedicatedcode.reitti.dto.workbench.MovedPointDto;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitRequest;
import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import com.dedicatedcode.reitti.service.workbench.WorkbenchService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class H3CellUpdateJobTest {
    @Autowired
    private WorkbenchService workbenchService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private RocksDBH3Service rocksDBH3Service;

    @Autowired
    private SpatialCoverageService spatialCoverageService;

    @Autowired
    private TestJdbcService testJdbcService;

    @Test
    void shouldHandlePromotionOfPoints() {
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

    @Test
    void shouldHandleDeletionOfPoints() {
        Awaitility.await().atMost(60, TimeUnit.SECONDS)
                .until(() -> rocksDBH3Service.isAvailable());

        User user = this.testingService.randomUser();
        Device device = this.testingService.findDefaultDevice(user);
        this.testingService.importData(user, "/data/gpx/20250618.gpx");
        this.testingService.awaitDataImport(30);

        Optional<CoverageInformation> luebeck = spatialCoverageService.getCoverageInformation(user, device, 27027, Locale.GERMAN);
        Optional<CoverageInformation> innenstadt = spatialCoverageService.getCoverageInformation(user, device, 367855, Locale.GERMAN);
        Optional<CoverageInformation> sanktJuergen = spatialCoverageService.getCoverageInformation(user, device, 367868, Locale.GERMAN);
        Optional<CoverageInformation> sanktGertrud = spatialCoverageService.getCoverageInformation(user, device, 367872, Locale.GERMAN);

        assertTrue(luebeck.isPresent());
        assertFalse(innenstadt.isPresent());
        assertTrue(sanktJuergen.isPresent());
        assertTrue(sanktGertrud.isPresent());

        //now we delete all points after 07:45 -> should remove the cell for sankt gertrud
        List<Long> pointsToDelete = this.testJdbcService.findSourcePointsAfter(user, "2025-06-18T05:45:00Z");
        WorkbenchCommitRequest request = new WorkbenchCommitRequest();
        EditStoreDto editStore = new EditStoreDto();
        editStore.setDeletedPoints(pointsToDelete.stream().map(p -> {
            DeletedPointDto deletedPointDto = new DeletedPointDto();
            deletedPointDto.setSourceId(p);
            return deletedPointDto;
        }).toList());
        request.setEditStore(editStore);
        workbenchService.applyCommit(user, request);

        //await recalculation
        this.testingService.awaitDataImport(100);


        sanktGertrud = spatialCoverageService.getCoverageInformation(user, device, 367872, Locale.GERMAN);
        assertFalse(sanktGertrud.isPresent());
    }

    @Test
    void shouldHandleMovementOfPoints() {
        Awaitility.await().atMost(60, TimeUnit.SECONDS)
                .until(() -> rocksDBH3Service.isAvailable());

        User user = this.testingService.randomUser();
        Device device = this.testingService.findDefaultDevice(user);
        this.testingService.importData(user, "/data/gpx/20250618.gpx");
        this.testingService.awaitDataImport(30);

        Optional<CoverageInformation> luebeck = spatialCoverageService.getCoverageInformation(user, device, 27027, Locale.GERMAN);
        Optional<CoverageInformation> innenstadt = spatialCoverageService.getCoverageInformation(user, device, 367855, Locale.GERMAN);
        Optional<CoverageInformation> sanktJuergen = spatialCoverageService.getCoverageInformation(user, device, 367868, Locale.GERMAN);
        Optional<CoverageInformation> sanktGertrud = spatialCoverageService.getCoverageInformation(user, device, 367872, Locale.GERMAN);

        assertTrue(luebeck.isPresent());
        assertFalse(innenstadt.isPresent());
        assertTrue(sanktJuergen.isPresent());
        assertTrue(sanktGertrud.isPresent());

        //now we move all points after 07:45 -> should remove the cell for sankt gertrud and add one for Bad Oldesloe
        List<Long> pointsToDelete = this.testJdbcService.findSourcePointsAfter(user, "2025-06-18T05:45:00Z");
        WorkbenchCommitRequest request = new WorkbenchCommitRequest();
        EditStoreDto editStore = new EditStoreDto();
        editStore.setMovedPoints(pointsToDelete.stream().map(p -> {
            MovedPointDto dto = new MovedPointDto(); //we move them all to Bad Oldesloe
            dto.setSourceId(p);
            dto.setLat(53.80837);
            dto.setLng(10.37092);
            return dto;
        }).toList());
        request.setEditStore(editStore);
        workbenchService.applyCommit(user, request);

        //await recalculation
        this.testingService.awaitDataImport(100);


        sanktGertrud = spatialCoverageService.getCoverageInformation(user, device, 367872, Locale.GERMAN);
        assertFalse(sanktGertrud.isPresent());
        Optional<CoverageInformation> badOldesloe = spatialCoverageService.getCoverageInformation(user, device, 532325, Locale.GERMAN);
        assertTrue(badOldesloe.isPresent());
        assertEquals("Bad Oldesloe", badOldesloe.get().name());
        assertEquals(1, badOldesloe.get().visitedCells());
    }
}