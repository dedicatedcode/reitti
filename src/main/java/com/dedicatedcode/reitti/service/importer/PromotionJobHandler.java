package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.jobs.PromotionInflightGuard;
import com.dedicatedcode.reitti.service.processing.LiveModeOnlyUpdateTask;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService.PromotionResult;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@DisallowConcurrentExecution
public class PromotionJobHandler implements Job {
    private static final Logger log = LoggerFactory.getLogger(PromotionJobHandler.class);
    private final LocationPointStagingService stagingService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository metadataRepository;
    private final UserNotificationService userNotificationService;
    private final PromotionInflightGuard promotionInflightGuard;
    private final JobDetail locationDataCleanupTask;
    private final JobDetail liveModeOnlyUpdateTask;
    private final JobDetail promotionTask;

    public PromotionJobHandler(LocationPointStagingService stagingService,
                               UserJdbcService userJdbcService,
                               DeviceJdbcService deviceJdbcService,
                               JobSchedulingService jobSchedulingService,
                               JobMetadataRepository metadataRepository,
                               UserNotificationService userNotificationService,
                               PromotionInflightGuard promotionInflightGuard,
                               @Qualifier("locationDataCleanupJob") JobDetail locationDataCleanupTask,
                               @Qualifier("liveModeUserUpdateJob") JobDetail liveModeOnlyUpdateTask,
                               @Qualifier("promotionJob") JobDetail promotionTask) {
        this.stagingService = stagingService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.metadataRepository = metadataRepository;
        this.userNotificationService = userNotificationService;
        this.promotionInflightGuard = promotionInflightGuard;
        this.locationDataCleanupTask = locationDataCleanupTask;
        this.liveModeOnlyUpdateTask = liveModeOnlyUpdateTask;
        this.promotionTask = promotionTask;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = TaskData.fromJson((String) context.getMergedJobDataMap().get("data"));
        UUID jobId = data.getJobId();
        User user = userJdbcService.findById(data.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + data.userId + "] not found"));
        String partitionKey = data.getPartitionKey();
        try {
            metadataRepository.updateProgress(jobId, 0, 3, "Promoting points");
            PromotionResult promotionResult = this.stagingService.promote(user, partitionKey);
            metadataRepository.updateProgress(jobId, 1, 3, "Dropping partition");

            log.debug("Promoted [{}] points into live table", promotionResult.promotedCount());
            if (data.isManual()) {
                this.stagingService.dropPartition(partitionKey);
            }

            if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
                metadataRepository.updateProgress(jobId, 2, 3, "Live data only, skipping cleanup");

                if (promotionResult.hasPromoted()) {
                    TimeRange promotedRange = promotionResult.promotedRange();
                    this.jobSchedulingService.enqueueTask(liveModeOnlyUpdateTask,
                                                          new LiveModeOnlyUpdateTask.TaskData(user.getId(), resolveDeviceId(user, data), promotedRange.start(), promotedRange.end()).withParentJobId(data.getParentJobId()),
                                                          JobSchedulingService.Metadata.builder()
                                                                  .user(user)
                                                                  .jobType(JobType.LOCATION_PROCESSING)
                                                                  .friendlyName("Location Data Cleanup")
                                                                  .build());
                }
            } else {
                metadataRepository.updateProgress(jobId, 2, 3, "Scheduling cleanup job");

                if (promotionResult.hasPromoted()) {
                    TimeRange promotedRange = promotionResult.promotedRange();
                    this.userNotificationService.newLocationData(user, resolveDevice(user, data), promotedRange);
                    this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                          new LocationDataCleanupTask.TaskData(user.getId(), resolveDeviceId(user, data), promotedRange.start(), promotedRange.end()).withParentJobId(data.getParentJobId()),
                                                          JobSchedulingService.Metadata.builder()
                                                                  .user(user)
                                                                  .jobType(JobType.LOCATION_DATA_CLEANUP)
                                                                  .friendlyName("Location Data Cleanup")
                                                                  .build());
                } else {
                    log.debug("No points to promote for partitionKey [{}]", partitionKey);
                }
                metadataRepository.updateProgress(jobId, 3, 3, "Done");
            }
        } finally {
            promotionInflightGuard.release(partitionKey);
        }

        if (this.stagingService.hasUnpromotedPoints(partitionKey) && promotionInflightGuard.tryAcquire(partitionKey)) {
            try {
                this.jobSchedulingService.enqueueTask(promotionTask,
                                                      new TaskData(user.getId(), resolveDeviceId(user, data), partitionKey, false),
                                                      JobSchedulingService.Metadata.builder()
                                                              .user(user)
                                                              .jobType(JobType.GPS_INGESTION)
                                                              .friendlyName("GPS Data Promotion").build());
            } catch (RuntimeException e) {
                promotionInflightGuard.release(partitionKey);
                throw e;
            }
        }
    }

    private Device resolveDevice(User user, TaskData data) {
        if (data.deviceId == null) {
            return null;
        }
        return deviceJdbcService.find(user, data.deviceId).orElseThrow(() -> new IllegalArgumentException("Device with id [" + data.deviceId + "] not found for user [" + user.getUsername() + "]"));
    }

    private Long resolveDeviceId(User user, TaskData data) {
        Device device = resolveDevice(user, data);
        return device != null ? device.id() : null;
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final Long userId;
        private final Long deviceId;
        private final String partitionKey;
        private final boolean isManual;

        public TaskData(Long userId, Long deviceId, String partitionKey, boolean isManual) {
            this(userId, deviceId, partitionKey, isManual, null, null);
        }

        @JsonCreator
        public TaskData(@JsonProperty("userId") Long userId,
                        @JsonProperty("deviceId") Long deviceId,
                        @JsonProperty("partitionKey") String partitionKey,
                        @JsonProperty("isManual") boolean isManual,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.deviceId = deviceId;
            this.partitionKey = partitionKey;
            this.isManual = isManual;
        }

        public String getPartitionKey() {
            return partitionKey;
        }

        public boolean isManual() {
            return isManual;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public String toString() {
            return "PromotionTaskData[" +
                    "userId=" + userId + ", " +
                    "deviceId=" + deviceId + ", " +
                    "partitionKey=" + partitionKey + ", " +
                    "isManual=" + isManual + ", " +
                    "parentJobId=" + parentJobId + ']';
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, deviceId, partitionKey, isManual, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, deviceId, partitionKey, isManual, jobId, parentJobId);
        }
    }
}
