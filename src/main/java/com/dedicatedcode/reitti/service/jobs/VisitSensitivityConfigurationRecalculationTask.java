package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.processing.RecalculationState;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import com.dedicatedcode.reitti.service.processing.UserProcessingLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@DisallowConcurrentExecution
public class VisitSensitivityConfigurationRecalculationTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(VisitSensitivityConfigurationRecalculationTask.class);
    private final VisitDetectionParametersJdbcService configurationService;
    private final UserJdbcService userJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository jobMetadataRepository;
    private final JobDetail processingPipelineTask;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final UserProcessingLock userProcessingLock;

    public VisitSensitivityConfigurationRecalculationTask(VisitDetectionParametersJdbcService configurationService,
                                                          UserJdbcService userJdbcService,
                                                          JobSchedulingService jobSchedulingService,
                                                          JobMetadataRepository jobMetadataRepository,
                                                          @Qualifier("processingPipelineJob") JobDetail processingPipelineTask,
                                                          TripJdbcService tripJdbcService,
                                                          ProcessedVisitJdbcService processedVisitJdbcService,
                                                          SignificantPlaceJdbcService significantPlaceJdbcService,
                                                          UserProcessingLock userProcessingLock) {
        this.configurationService = configurationService;
        this.userJdbcService = userJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.jobMetadataRepository = jobMetadataRepository;
        this.processingPipelineTask = processingPipelineTask;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.userProcessingLock = userProcessingLock;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData taskData = TaskData.fromJson((String) context.getMergedJobDataMap().get("data"));
        User user = userJdbcService.findById(taskData.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + taskData.userId + "] not found"));
        log.debug("Executing DataRecalculationJob for [{}]", user);
        try {
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 0, 4, "Waiting for running processing to finish ...");
            userProcessingLock.locked(user, () -> {
                this.jobMetadataRepository.updateProgress(taskData.getJobId(), 1, 4, "Deleting Trips ...");
                tripJdbcService.deleteAllForUser(user);
                this.jobMetadataRepository.updateProgress(taskData.getJobId(), 2, 4, "Deleting Visits ...");
                processedVisitJdbcService.deleteAllForUser(user);
                this.jobMetadataRepository.updateProgress(taskData.getJobId(), 3, 4, "Deleting Places ...");
                significantPlaceJdbcService.deleteForUser(user);
                this.configurationService.findAllConfigurationsForUser(user)
                        .forEach(config -> this.configurationService.updateConfiguration(user, config.withRecalculationState(RecalculationState.DONE)));
                log.debug("Starting recalculation of all configurations");
                jobSchedulingService.enqueueTaskAfterCommit(processingPipelineTask,
                                                 new ProcessingPipelineTask.TaskData(user.getUsername(), null, null)
                                                         .withFullReprocess()
                                                         .withParentJobId(taskData.getParentJobId()),
                                                 new JobSchedulingService.Metadata(user, JobType.LOCATION_PROCESSING, "Processing location data ..."));
            });
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 4, 4, "Done");
        } catch (Exception e) {
            log.error("Error recalculating visit sensitivity configuration for user [{}]", user.getUsername(), e);
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 0, 4, "Failed");
            throw new JobExecutionException("Failed to recalculate visit sensitivity configuration for user [" + user.getUsername() + "]", e);
        }
    }

    public static class TaskData extends JobContext<TaskData> {

        private final Long userId;

        public TaskData(Long userId) {
            this(userId, null, null);
        }

        @JsonCreator
        private TaskData(@JsonProperty("userId") Long userId,
                         @JsonProperty("jobId") UUID jobId,
                         @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, jobId, parentJobId);
        }
    }
}
