package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.PatchDeviceOntoTimelineTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class JobChainParentPropagationTest {

    @Autowired
    private PatchDeviceOntoTimelineTask patchDeviceOntoTimelineTask;

    @Autowired
    private LocationDataCleanupTask locationDataCleanupTask;

    @Autowired
    private JobSchedulingService jobSchedulingService;

    @Autowired
    private JobMetadataRepository jobMetadataRepository;

    @Autowired
    private TestingService testingService;

    @Test
    void patchDeviceShouldFlattenSuccessorsUnderTheSameParent() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        UUID parentId = jobSchedulingService.createParentJob(user, JobType.MANUAL_MODIFICATION, "chain-test-patch-parent");
        try {
            patchDeviceOntoTimelineTask.execute(new PatchDeviceOntoTimelineTask.TaskData(user, device,
                    Instant.now().minusSeconds(3600), Instant.now()).withParentJobId(parentId));

            // timeline stitching and the following visit detection must both become siblings
            // under the same parent instead of being nested below the patch task
            await().atMost(30, TimeUnit.SECONDS).until(() -> {
                List<JobMetadataRepository.JobMetadata> children = jobMetadataRepository.findByParentJobId(parentId);
                return children.stream().anyMatch(j -> j.getJobType() == JobType.TIMELINE_STITCHING)
                        && children.stream().anyMatch(j -> j.getJobType() == JobType.VISIT_TRIP_DETECTION);
            });

            // the stitching task itself must not have spawned its own children
            JobMetadataRepository.JobMetadata stitchingRow = jobMetadataRepository.findByParentJobId(parentId).stream()
                    .filter(j -> j.getJobType() == JobType.TIMELINE_STITCHING)
                    .findFirst()
                    .orElseThrow();
            assertEquals(List.of(), jobMetadataRepository.findByParentJobId(stitchingRow.getId()));
        } finally {
            jobSchedulingService.cancel(parentId);
        }
    }

    @Test
    void locationDataCleanupShouldFlattenSuccessorsUnderTheSameParent() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        UUID parentId = jobSchedulingService.createParentJob(user, JobType.LOCATION_DATA_CLEANUP, "chain-test-cleanup-parent");
        try {
            locationDataCleanupTask.execute(new LocationDataCleanupTask.TaskData(user, device,
                    Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)).withParentJobId(parentId));

            await().atMost(30, TimeUnit.SECONDS).until(() -> jobMetadataRepository.findByParentJobId(parentId).stream()
                    .anyMatch(j -> j.getJobType() == JobType.VISIT_TRIP_DETECTION));
        } finally {
            jobSchedulingService.cancel(parentId);
        }
    }
}
