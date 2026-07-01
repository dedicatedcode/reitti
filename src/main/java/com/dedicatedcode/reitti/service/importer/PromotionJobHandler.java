package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PromotionJobHandler implements Job {
    private static final Logger log = LoggerFactory.getLogger(PromotionJobHandler.class);
    private final LocationPointStagingService stagingService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository metadataRepository;
    private final UserNotificationService userNotificationService;
    private final JobDetail locationDataCleanupTask;

    public PromotionJobHandler(LocationPointStagingService stagingService,
                               JobSchedulingService jobSchedulingService,
                               JobMetadataRepository metadataRepository,
                               UserNotificationService userNotificationService,
                               @Qualifier("locationDataCleanupJob") JobDetail locationDataCleanupTask) {
        this.stagingService = stagingService;
        this.jobSchedulingService = jobSchedulingService;
        this.metadataRepository = metadataRepository;
        this.userNotificationService = userNotificationService;
        this.locationDataCleanupTask = locationDataCleanupTask;
    }
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        TaskData data = (TaskData) dataMap.get("data");
        execute(data);
    }

    public void execute(TaskData data) {
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
            this.userNotificationService.newLocationData(user, data.device, timeRange);
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.LOCATION_DATA_CLEANUP)
                    .friendlyName("Location Data Cleanup")
                    .build();
            this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                  new LocationDataCleanupTask.TaskData(user, data.getDevice(), timeRange.start(), timeRange.end()).withParentJobId(data.getParentJobId()),
                                                  metadata);
        } else {
            log.debug("No points to promote, timerange was [{}]", timeRange);
        }
        metadataRepository.updateProgress(jobId, 3, 3, "Done");
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final User user;
        private final Device device;
        private final String partitionKey;
        private final boolean isManual;

        public TaskData(User user, Device device, String partitionKey, boolean isManual) {
            this.user = user;
            this.device = device;
            this.partitionKey = partitionKey;
            this.isManual = isManual;
        }

        public TaskData(User user, Device device, String partitionKey, boolean isManual, UUID jobId, UUID parentJobId) {
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
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, device, partitionKey, isManual, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, device, partitionKey, isManual, jobId, parentJobId);
        }
    }
}
