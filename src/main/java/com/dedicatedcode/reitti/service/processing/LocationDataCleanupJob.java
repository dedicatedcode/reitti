package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Component
public class LocationDataCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(LocationDataCleanupJob.class);
    private final LocationDataDensityNormalizer locationDataDensityNormalizer;
    private final AnomalyProcessingService anomalyProcessingService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserJdbcService userJdbcService;
    private final JobSchedulingService jobScheduler;
    private final Task<TriggerProcessingEvent> processingEventTask;
    private final JobMetadataRepository metadataRepository;

    public LocationDataCleanupJob(LocationDataDensityNormalizer locationDataDensityNormalizer,
                                  AnomalyProcessingService anomalyProcessingService,
                                  UserSettingsJdbcService userSettingsJdbcService,
                                  UserJdbcService userJdbcService,
                                  JobSchedulingService jobScheduler,
                                  Task<TriggerProcessingEvent> processingEventTask, JobMetadataRepository metadataRepository) {
        this.locationDataDensityNormalizer = locationDataDensityNormalizer;
        this.anomalyProcessingService = anomalyProcessingService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userJdbcService = userJdbcService;
        this.jobScheduler = jobScheduler;
        this.processingEventTask = processingEventTask;
        this.metadataRepository = metadataRepository;
    }

    public void execute(TaskData data) {
        UUID jobId = data.getJobId();
        User user = data.getUser();
        Device device = data.getDevice();
        Instant start = data.getStart();
        Instant end = data.getEnd();
        log.debug("Starting LocationDataCleanupJob for user [{}] and device [{}] between {} and {}", user, device, start, end);
        this.metadataRepository.updateProgress(jobId, 0,4, "Anomaly processing started ...");
        anomalyProcessingService.processAndMarkAnomalies(user, start, end);
        this.metadataRepository.updateProgress(jobId, 1,4, "Density normalization started ...");
        locationDataDensityNormalizer.normalize(user, new TimeRange(start, end));
        this.metadataRepository.updateProgress(jobId, 2,4, "Update user data started ...");
        userSettingsJdbcService.updateNewestData(user, end);
        this.userJdbcService.setLastDataModificationAt(user, Instant.now());
        this.metadataRepository.updateProgress(jobId, 3,4, "Schedule processing events started ...");
        if (device == null) {
            jobScheduler.scheduleTask(processingEventTask,
                                      new TriggerProcessingEvent(user.getUsername(), null, null).withParentJobId(jobId),
                                      Instant.now().plus(10, ChronoUnit.SECONDS),
                                      JobSchedulingService.Metadata.builder().jobType(VISIT_TRIP_DETECTION)
                                              .user(user)
                                              .friendlyName("Detect Visits and Trips").build()
            );
        }

        this.metadataRepository.updateProgress(jobId, 4,4, "Finished");
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
