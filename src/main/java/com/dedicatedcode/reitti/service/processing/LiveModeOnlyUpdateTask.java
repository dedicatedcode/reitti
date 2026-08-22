package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.UserNotificationService;
import org.quartz.*;
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
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final JobMetadataRepository metadataRepository;
    private final UserNotificationService userNotificationService;
    private final UserProcessingLock userProcessingLock;

    public LiveModeOnlyUpdateTask(
            UserJdbcService userJdbcService,
            SourceLocationPointJdbcService sourceLocationPointJdbcService,
            RawLocationPointJdbcService rawLocationPointJdbcService,
            JobMetadataRepository metadataRepository,
            UserNotificationService userNotificationService,
            UserProcessingLock userProcessingLock) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.metadataRepository = metadataRepository;
        this.userNotificationService = userNotificationService;
        this.userProcessingLock = userProcessingLock;
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
        Device device = data.getDevice();
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
            this.userNotificationService.newLocationData(user, data.device, TimeRange.of(start, end));
            this.metadataRepository.updateProgress(jobId, 3, 4, "Finished");
        });
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final User user;
        private final Device device;
        private final Instant start;
        private final Instant end;

        public TaskData(User user, Device device, Instant start, Instant end) {
            this(user, device, start, end, null, null);
        }

        public TaskData(User user, Device device, Instant start, Instant end, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.device = device;
            this.start = start;
            this.end = end;
        }

        public User getUser() {
            return user;
        }

        public Device getDevice() {
            return device;
        }

        public Instant getStart() {
            return start;
        }

        public Instant getEnd() {
            return end;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, device, start, end, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, device, start, end, jobId, parentJobId);
        }

        @Override
        public String toString() {
            return "TaskData[" +
                    "user=" + user + ", " +
                    "device=" + device + ", " +
                    "start=" + start + ", " +
                    "end=" + end + "]";
        }
    }
}
