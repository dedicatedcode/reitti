package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Service
@DisallowConcurrentExecution
public class UpdateCuratedTimelineTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(UpdateCuratedTimelineTask.class);

    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final SyntheticPointInserter syntheticPointInserter;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail processingPipelineTask;
    private final UserProcessingLock userProcessingLock;
    private final JobMetadataRepository jobMetadataRepository;

    public UpdateCuratedTimelineTask(RawLocationPointJdbcService rawLocationPointJdbcService,
                                     UserJdbcService userJdbcService,
                                     DeviceJdbcService deviceJdbcService,
                                     SyntheticPointInserter syntheticPointInserter,
                                     JobSchedulingService jobSchedulingService,
                                     @Qualifier("processingPipelineJob") JobDetail processingPipelineTask,
                                     UserProcessingLock userProcessingLock,
                                     JobMetadataRepository jobMetadataRepository) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.syntheticPointInserter = syntheticPointInserter;
        this.jobSchedulingService = jobSchedulingService;
        this.processingPipelineTask = processingPipelineTask;
        this.userProcessingLock = userProcessingLock;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        execute(TaskData.fromJson((String) context.getMergedJobDataMap().get("data")));
    }

    public void execute(TaskData data) {
        User user = userJdbcService.findById(data.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + data.userId + "] not found"));
        Device device = deviceJdbcService.find(user, data.deviceId).orElseThrow(() -> new IllegalArgumentException("Device with id [" + data.deviceId + "] not found for user [" + user.getUsername() + "]"));
        log.debug("Starting updating main timeline for user [{}] and device[{}] in timeRange [{}]", user, device, data.timeRange);
        UUID jobId = data.getJobId();
        userProcessingLock.locked(user, () -> {
            //1. clear main timeline
            this.jobMetadataRepository.updateProgress(jobId, 0, 4, "Clearing main timeline ...");
            this.rawLocationPointJdbcService.dropForReSeeding(user, data.timeRange);
            //2. update main timeline from view
            this.jobMetadataRepository.updateProgress(jobId, 1, 4, "Updating main timeline ...");
            int updatedCount = this.rawLocationPointJdbcService.updateFromDevices(user, data.timeRange);
            log.debug("Updated {} timeline points for user [{}] and device[{}] in timeRange [{}]", updatedCount, user, device, data.timeRange);
            //3. insert new possible synthetic points
            this.jobMetadataRepository.updateProgress(jobId, 2, 4, "Inserting synthetic points ...");
            this.syntheticPointInserter.fillGaps(user, data.timeRange);
            //4. trigger new processing job
            this.jobMetadataRepository.updateProgress(jobId, 3, 4, "Scheduling visit detection ...");
            this.jobSchedulingService.enqueueTask(processingPipelineTask,
                                     new ProcessingPipelineTask.TaskData(user.getUsername(), null, null).withParentJobId(data.getParentJobId()),
                                                  JobSchedulingService.Metadata.builder().jobType(VISIT_TRIP_DETECTION)
                                                          .user(user)
                                                          .friendlyName("Detect Visits and Trips").build());
            this.jobMetadataRepository.updateProgress(jobId, 4, 4, "Done");
        });

    }

    public static class TaskData extends JobContext<TaskData> {

        private final Long userId;
        private final Long deviceId;
        private final TimeRange timeRange;

        public TaskData(Long userId, Long deviceId, TimeRange timeRange) {
            this(userId, deviceId, timeRange, null, null);
        }

        @JsonCreator
        public TaskData(@JsonProperty("userId") Long userId,
                        @JsonProperty("deviceId") Long deviceId,
                        @JsonProperty("timeRange") TimeRange timeRange,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.deviceId = deviceId;
            this.timeRange = timeRange;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, deviceId, timeRange, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, deviceId, timeRange, jobId, parentJobId);
        }
    }
}
