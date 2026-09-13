package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


@Component
@DisallowConcurrentExecution
public class LiveModeOnlyUpdateTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(LiveModeOnlyUpdateTask.class);
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final JobMetadataRepository metadataRepository;
    private final UserNotificationService userNotificationService;
    private final UserProcessingLock userProcessingLock;

    public LiveModeOnlyUpdateTask(
            UserJdbcService userJdbcService,
            DeviceJdbcService deviceJdbcService,
            SourceLocationPointJdbcService sourceLocationPointJdbcService,
            RawLocationPointJdbcService rawLocationPointJdbcService,
            JobMetadataRepository metadataRepository,
            UserNotificationService userNotificationService,
            UserProcessingLock userProcessingLock) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.metadataRepository = metadataRepository;
        this.userNotificationService = userNotificationService;
        this.userProcessingLock = userProcessingLock;
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

        log.debug("Starting LiveModeOnlyUpdateTask for user [{}] and device [{}] between {} and {}", user, device, start, end);
        this.metadataRepository.updateProgress(jobId, 0, 4, "Cleaning up source points ...");

        userProcessingLock.locked(user, () -> {
            this.sourceLocationPointJdbcService.deleteAllExceptLatestForUserAndDevice(user, device);
            this.metadataRepository.updateProgress(jobId, 1, 4, "Updating latest location ...");

            Optional<SourceLocationPoint> latestSourcePoint = this.sourceLocationPointJdbcService.findLatest(user, device);
            latestSourcePoint.ifPresent(point -> {
                LocationPoint locationPoint = new LocationPoint();
                locationPoint.setTimestamp(point.getTimestamp());
                locationPoint.setLatitude(point.getGeom().latitude());
                locationPoint.setLongitude(point.getGeom().longitude());
                locationPoint.setAccuracyMeters(point.getAccuracyMeters());
                locationPoint.setElevationMeters(point.getElevationMeters());
                this.rawLocationPointJdbcService.replaceLatestForUser(user, locationPoint);
            });

            this.metadataRepository.updateProgress(jobId, 2, 4, "Updating last modification timestamp ...");
            this.userJdbcService.setLastDataModificationAt(user, Instant.now());
            this.userNotificationService.newLocationData(user, device, TimeRange.of(start, end));
            this.metadataRepository.updateProgress(jobId, 3, 4, "Finished");
        });
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
