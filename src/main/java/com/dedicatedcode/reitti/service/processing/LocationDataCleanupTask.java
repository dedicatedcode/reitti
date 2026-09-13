package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Component
@DisallowConcurrentExecution
public class LocationDataCleanupTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(LocationDataCleanupTask.class);
    private final ExcessDensityHandler excessDensityHandler;
    private final AnomalyProcessingService anomalyProcessingService;
    private final ProcessingWindowResolver processingWindowResolver;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final JobSchedulingService jobScheduler;
    private final JobDetail updateCuratedTimelineTask;
    private final JobMetadataRepository metadataRepository;

    public LocationDataCleanupTask(ExcessDensityHandler excessDensityHandler,
                                   AnomalyProcessingService anomalyProcessingService,
                                   ProcessingWindowResolver processingWindowResolver,
                                   UserSettingsJdbcService userSettingsJdbcService,
                                   UserJdbcService userJdbcService,
                                   DeviceJdbcService deviceJdbcService,
                                   JobSchedulingService jobScheduler,
                                   @Qualifier("updateCuratedTimelineJob") JobDetail updateCuratedTimelineTask,
                                   JobMetadataRepository metadataRepository) {
        this.excessDensityHandler = excessDensityHandler;
        this.anomalyProcessingService = anomalyProcessingService;
        this.processingWindowResolver = processingWindowResolver;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.jobScheduler = jobScheduler;
        this.updateCuratedTimelineTask = updateCuratedTimelineTask;
        this.metadataRepository = metadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        execute(TaskData.fromJson((String) context.getMergedJobDataMap().get("data")));
    }

    public void execute(TaskData data) {
        UUID jobId = data.getJobId();
        User user = userJdbcService.findById(data.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + data.userId + "] not found"));
        Device device = deviceJdbcService.find(user, data.deviceId).orElseThrow(() -> new IllegalArgumentException("Device with id [" + data.deviceId + "] not found for user [" + user.getUsername() + "]"));
        Instant start = data.getStart();
        Instant end = data.getEnd();
        log.debug("Starting LocationDataCleanupJob for user [{}] and device [{}] between {} and {}", user, device, start, end);
        this.metadataRepository.updateProgress(jobId, 0,4, "Anomaly processing started ...");
        TimeRange window = processingWindowResolver.resolve(user, device, new TimeRange(start, end));
        TimeRange processedTimeRange = anomalyProcessingService.processAndMarkAnomalies(user, device, window.start(), window.end());
        this.metadataRepository.updateProgress(jobId, 1,4, "Density normalization started ...");
        TimeRange densityTimeRange = excessDensityHandler.handleExcess(user, device, window);
        this.metadataRepository.updateProgress(jobId, 2,4, "Update user data started ...");
        this.userSettingsJdbcService.updateNewestData(user, end);
        this.userJdbcService.setLastDataModificationAt(user, Instant.now());
        this.metadataRepository.updateProgress(jobId, 3,4, "Schedule processing events started ...");
        if (device.defaultDevice()) {
            jobScheduler.enqueueTask(updateCuratedTimelineTask,
                                      new UpdateCuratedTimelineTask.TaskData(user.getId(), device.id(), processedTimeRange.extend(densityTimeRange)).withParentJobId(data.getParentJobId()),
                                      JobSchedulingService.Metadata.builder().jobType(VISIT_TRIP_DETECTION)
                                              .user(user)
                                              .friendlyName("Detect Visits and Trips").build()
            );
        }

        this.metadataRepository.updateProgress(jobId, 4,4, "Finished");
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final Long userId;
        private final Long deviceId;
        private final Instant start;
        private final Instant end;

        public TaskData(Long userId, Long deviceId, Instant start, Instant end) {
            this(userId, deviceId, start, end, null, null);
        }

        @JsonCreator
        public TaskData(@JsonProperty("userId") Long userId,
                        @JsonProperty("deviceId") Long deviceId,
                        @JsonProperty("start") Instant start,
                        @JsonProperty("end") Instant end,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.deviceId = deviceId;
            this.start = start;
            this.end = end;
        }

        public Instant getStart() {
            return start;
        }

        public Instant getEnd() {
            return end;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, deviceId, start, end, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, deviceId, start, end, jobId, parentJobId);
        }

        @Override
        public String toString() {
            return "TaskData[" +
                    "userId=" + userId + ", " +
                    "deviceId=" + deviceId + ", " +
                    "start=" + start + ", " +
                    "end=" + end + "]";
        }
    }
}
