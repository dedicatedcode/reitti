package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupJob;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
public class PromotionJobHandler {
    private static final Logger log = LoggerFactory.getLogger(PromotionJobHandler.class);
    private final LocationPointStagingService stagingService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository metadataRepository;
    private final Task<LocationDataCleanupJob.TaskData> locationDataCleanupTask;

    public PromotionJobHandler(LocationPointStagingService stagingService,
                               JobSchedulingService jobSchedulingService, JobMetadataRepository metadataRepository,
                               Task<LocationDataCleanupJob.TaskData> locationDataCleanupTask) {
        this.stagingService = stagingService;
        this.jobSchedulingService = jobSchedulingService;
        this.metadataRepository = metadataRepository;
        this.locationDataCleanupTask = locationDataCleanupTask;
    }

    public void execute(UUID jobId, User user, Device device, String partitionKey, UUID parentJobId, boolean dropPartition) {
        TimeRange timeRange = this.stagingService.getTimeRange(partitionKey);
        metadataRepository.updateProgress(jobId, 0, 3, "Promoting points");
        int promote = this.stagingService.promote(partitionKey);
        metadataRepository.updateProgress(jobId, 1, 3, "Droping partition");

        log.debug("Promoted [{}] points into live table", promote);
        if (dropPartition) {
            this.stagingService.dropPartition(partitionKey);
        }
        metadataRepository.updateProgress(jobId, 2, 3, "Scheduling cleanup job");

        if (promote > 0) {
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.LOCATION_DATA_CLEANUP)
                    .friendlyName("Location Data Cleanup")
                    .parentId(parentJobId)
                    .build();
            this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                  new LocationDataCleanupJob.TaskData(user, device, timeRange.start(), timeRange.end(), parentJobId),
                                                  metadata);
        } else {
            log.debug("No points to promote, timerange was [{}]", timeRange);
        }
        metadataRepository.updateProgress(jobId, 3, 3, "Done");
    }

    public record PromotionTaskData(
            User user,
            Device device,
            String partitionKey,
            boolean isManual,
            UUID parentJobId
    ) implements Serializable {
    }
}
