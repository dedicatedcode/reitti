package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.processing.RecalculationState;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.JobContext;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VisitSensitivityConfigurationRecalculationTask {
    private static final Logger log = LoggerFactory.getLogger(VisitSensitivityConfigurationRecalculationTask.class);
    private final VisitDetectionParametersJdbcService configurationService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository jobMetadataRepository;
    private final Task<TriggerProcessingEvent> processingEventTask;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public VisitSensitivityConfigurationRecalculationTask(VisitDetectionParametersJdbcService configurationService,
                                                          JobSchedulingService jobSchedulingService,
                                                          JobMetadataRepository jobMetadataRepository,
                                                          Task<TriggerProcessingEvent> processingEventTask,
                                                          TripJdbcService tripJdbcService,
                                                          ProcessedVisitJdbcService processedVisitJdbcService,
                                                          SignificantPlaceJdbcService significantPlaceJdbcService, RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.configurationService = configurationService;
        this.jobSchedulingService = jobSchedulingService;
        this.jobMetadataRepository = jobMetadataRepository;
        this.processingEventTask = processingEventTask;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    public void execute(TaskData taskData) {
        User user = taskData.user;
        log.debug("Executing DataRecalculationJob for [{}]", user);
        try {
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 1, 5, "Deleting Trips ...");
            tripJdbcService.deleteAllForUser(user);
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 2, 5, "Deleting Visits ...");
            processedVisitJdbcService.deleteAllForUser(user);
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 3, 5, "Deleting Places ...");
            significantPlaceJdbcService.deleteForUser(user);
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 4, 5, "Flag points as unprocessed ...");
            rawLocationPointJdbcService.markAllAsUnprocessedForUser(user);
            this.configurationService.findAllConfigurationsForUser(user)
                    .forEach(config -> this.configurationService.updateConfiguration(config.withRecalculationState(RecalculationState.DONE)));
            log.debug("Starting recalculation of all configurations");
            this.jobMetadataRepository.updateProgress(taskData.getJobId(), 5, 5, "Starting recalculation ... ");
            jobSchedulingService.enqueueTask(processingEventTask,
                                             new TriggerProcessingEvent(user.getUsername(), null, null).withParentJobId(taskData.getJobId()),
                                             new JobSchedulingService.Metadata(user, JobType.LOCATION_PROCESSING, "Processing location data ..."));
        } catch (Exception e) {
            log.error("Error clearing time range", e);
        }

    }
    public static class TaskData extends JobContext<TaskData> {

        public final User user;

        public TaskData(User user) {
            this.user = user;
        }

        private TaskData(User user, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, jobId, parentJobId);
        }
    }
}
