package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestJdbcService;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.workbench.*;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.dedicatedcode.reitti.TestConstants.Points.*;
import static com.dedicatedcode.reitti.TestUtils.assertVisit;
import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class WorkbenchServiceTest {
    @Autowired
    private WorkbenchService candidate;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private TestJdbcService testJdbcService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = this.testingService.randomUser();
    }

    @Test
    void shouldRecalculateOnDeletion() {
        //First import a gpx file with 5 visits
        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = this.processedVisitJdbcService.findByUser(this.user);
        assertEquals(5, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-17T21:59:44.876Z", MOLTKESTR);

        List<Long> pointsToDelete = this.testJdbcService.findPointsForVisit(this.user, processedVisits.get(1));
        //Now delete every point in timerange "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z"
        WorkbenchCommitRequest request = new WorkbenchCommitRequest();
        EditStoreDto editStore = new EditStoreDto();
        editStore.setDeletedPoints(pointsToDelete.stream().map(p -> {
            DeletedPointDto deletedPointDto = new DeletedPointDto();
            deletedPointDto.setSourceId(p);
            return deletedPointDto;
        }).toList());
        request.setEditStore(editStore);
        candidate.applyCommit(this.user, request);

        //await recalculation
        this.testingService.awaitDataImport(100);

        //After recalculation is done, only 4 Visits should be left
        List<ProcessedVisit> processedVisitsAfterDeletion = this.processedVisitJdbcService.findByUser(this.user);
        assertEquals(4, processedVisitsAfterDeletion.size());

        assertVisit(processedVisitsAfterDeletion.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisitsAfterDeletion.get(1), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisitsAfterDeletion.get(2), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);
        assertVisit(processedVisitsAfterDeletion.get(3), "2025-06-17T13:21:28.334Z", "2025-06-17T21:59:44.876Z", MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnMove() {
        //First import a gpx file with 5 visits
        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = this.processedVisitJdbcService.findByUser(this.user);
        assertEquals(5, processedVisits.size());

        //Now we move all Points to GARTEN
        List<Long> pointsOfVisit = this.testJdbcService.findPointsForVisit(this.user, processedVisits.get(2));

        WorkbenchCommitRequest request = new WorkbenchCommitRequest();
        EditStoreDto editStore = new EditStoreDto();
        editStore.setMovedPoints(pointsOfVisit.stream().map(p -> {
            MovedPointDto movedPointDto = new MovedPointDto();
            movedPointDto.setSourceId(p);
            movedPointDto.setLat(GARTEN.latitude());
            movedPointDto.setLng(GARTEN.longitude());
            return movedPointDto;
        }).toList());
        request.setEditStore(editStore);

        candidate.applyCommit(this.user, request);

        //await recalculation
        this.testingService.awaitDataImport(60);

        //After recalculation is done, 5 Visits shall remain
        List<ProcessedVisit> updatedVisits = this.processedVisitJdbcService.findByUser(this.user);
        assertEquals(5, updatedVisits.size());
        //but visit number 2 should now be at GARTEN
        assertVisit(updatedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:22Z", GARTEN);
    }

    @Test
    void shouldStitchFinalTimeline() {
        Device device = this.testingService.createRandomDevice(user);
        //First, import a gpx file with 5 visits into the main timeline
        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");
        //Now import the same gpx file into the device
        testingService.importAndProcess(user, device, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = this.processedVisitJdbcService.findByUser(this.user);
        List<Long> pointsToDelete = this.testJdbcService.findPointsForVisit(this.user, processedVisits.get(1));
        //Now delete every point in timerange "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z"
        WorkbenchCommitRequest request = new WorkbenchCommitRequest();
        EditStoreDto editStore = new EditStoreDto();
        editStore.setDeletedPoints(pointsToDelete.stream().map(p -> {
            DeletedPointDto deletedPointDto = new DeletedPointDto();
            deletedPointDto.setSourceId(p);
            return deletedPointDto;
        }).toList());
        request.setEditStore(editStore);
        candidate.applyCommit(this.user, request);

        //await recalculation
        this.testingService.awaitDataImport(100);
        assertEquals(4, this.processedVisitJdbcService.findByUser(this.user).size());
        // now we removed the second visit from the main timeline, time to stich in the visit back from the new device

        WorkbenchCommitRequest stitchRequest = new WorkbenchCommitRequest();
        EditStoreDto stitchStore = new EditStoreDto();
        PatchDto patch = new PatchDto();
        patch.setDeviceId(String.valueOf(device.id()));
        patch.setSeq(1);
        patch.settStart(Instant.parse("2025-06-17T05:44:08.763Z").toEpochMilli());
        patch.settEnd(Instant.parse("2025-06-17T05:49:18.965Z").toEpochMilli());
        stitchStore.setPatches(Collections.singletonList(patch));
        stitchRequest.setEditStore(stitchStore);

        candidate.applyCommit(this.user, stitchRequest);

        //await recalculation
        this.testingService.awaitDataImport(60);

        processedVisits = this.processedVisitJdbcService.findByUser(this.user);
        assertEquals(5, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-17T21:59:44.876Z", MOLTKESTR);

    }
}