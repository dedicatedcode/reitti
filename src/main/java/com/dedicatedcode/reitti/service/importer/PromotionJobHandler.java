package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupJob;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    public void execute(PromotionTaskData data) {
        UUID jobId = data.getJobId();
        User user = data.getUser();
        String partitionKey = data.getPartitionKey();
        TimeRange timeRange = this.stagingService.getTimeRange(partitionKey);
        metadataRepository.updateProgress(jobId, 0, 3, "Promoting points");
        int promote = this.stagingService.promote(partitionKey);
        metadataRepository.updateProgress(jobId, 1, 3, "Dropping partition");

        log.debug("Promoted [{}] points into live table", promote);
        if (data.isManual()) {
            this.stagingService.dropPartition(partitionKey);
        }
        metadataRepository.updateProgress(jobId, 2, 3, "Scheduling cleanup job");

        if (promote > 0) {
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.LOCATION_DATA_CLEANUP)
                    .friendlyName("Location Data Cleanup")
                    .build();
            this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                  new LocationDataCleanupJob.TaskData(user, data.getDevice(), timeRange.start(), timeRange.end()).withParentJobId(data.getParentJobId()),
                                                  metadata);
        } else {
            log.debug("No points to promote, timerange was [{}]", timeRange);
        }
        metadataRepository.updateProgress(jobId, 3, 3, "Done");
    }

    public static final class PromotionTaskData extends JobContext<PromotionTaskData> {
        private final User user;
        private final Device device;
        private final String partitionKey;
        private final boolean isManual;

        public PromotionTaskData(User user, Device device, String partitionKey, boolean isManual) {
            this.user = user;
            this.device = device;
            this.partitionKey = partitionKey;
            this.isManual = isManual;
        }

        public PromotionTaskData(User user, Device device, String partitionKey, boolean isManual, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.device = device;
            this.partitionKey = partitionKey;
            this.isManual = isManual;
        }

        public User getUser() {
            return user;
        }

        public Device getDevice() {
            return device;
        }

        public String getPartitionKey() {
            return partitionKey;
        }

        public boolean isManual() {
            return isManual;
        }

        @Override
        public String toString() {
            return "PromotionTaskData[" +
                    "user=" + user + ", " +
                    "device=" + device + ", " +
                    "partitionKey=" + partitionKey + ", " +
                    "isManual=" + isManual + ", " +
                    "parentJobId=" + parentJobId + ']';
        }

        @Override
        public PromotionTaskData withJobId(UUID jobId) {
            return new PromotionTaskData(user, device, partitionKey, isManual, jobId, parentJobId);
        }

        @Override
        public PromotionTaskData withParentJobId(UUID parentJobId) {
            return new PromotionTaskData(user, device, partitionKey, isManual, jobId, parentJobId);
        }
    }
}
