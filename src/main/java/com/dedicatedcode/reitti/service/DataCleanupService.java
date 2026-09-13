package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Service
@DisallowConcurrentExecution
public class DataCleanupService implements Job {
    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final JobSchedulingService jobScheduler;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final JobDetail processingPipelineTask;
    private final JobMetadataRepository jobMetadataRepository;


    public DataCleanupService(TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              SignificantPlaceJdbcService significantPlaceJdbcService,
                              RawLocationPointJdbcService rawLocationPointJdbcService,
                              UserJdbcService userJdbcService,
                              JobSchedulingService jobScheduler, SignificantPlaceJdbcService placeJdbcService,
                              @Qualifier("processingPipelineJob") JobDetail processingPipelineTask,
                              JobMetadataRepository jobMetadataRepository) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.jobScheduler = jobScheduler;
        this.placeJdbcService = placeJdbcService;
        this.processingPipelineTask = processingPipelineTask;
        this.jobMetadataRepository = jobMetadataRepository;
    }
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        execute(TaskData.fromJson((String) context.getMergedJobDataMap().get("data")));
    }

    public void execute(TaskData taskData) {
        User user = userJdbcService.findById(taskData.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + taskData.userId + "] not found"));
        SignificantPlace updatedPlace = placeJdbcService.findById(taskData.placeId).orElseThrow(() -> new IllegalArgumentException("SignificantPlace with id [" + taskData.placeId + "] not found"));
        Long placeId = updatedPlace.getId();
        UUID jobId = taskData.getJobId();

        this.jobMetadataRepository.updateProgress(jobId, 0, 2, "Analyzing affected places ...");
        List<SignificantPlace> placesToRemove = placeJdbcService.findPlacesOverlappingWithPolygon(user.getId(), placeId, updatedPlace.getPolygon());
        List<SignificantPlace> placesToCheck = new ArrayList<>(placesToRemove);
        placesToCheck.add(updatedPlace);
        List<LocalDate> affectedDays = this.processedVisitJdbcService.getAffectedDays(placesToCheck);

        this.jobMetadataRepository.updateProgress(jobId, 1, 2, "Removing trips, visits, places and marking points unprocessed ...");
        cleanupForGeometryChange(user, placesToRemove, affectedDays, taskData.getParentJobId());

        this.jobMetadataRepository.updateProgress(jobId, 2, 2, "Done");
    }

    void cleanupForGeometryChange(User user, List<SignificantPlace> placesToRemove, List<LocalDate> affectedDays, UUID parentJobId) {
        long start = System.nanoTime();

        log.info("Cleanup for geometry change. Removing [{}] places and starting recalculation for days [{}]", placesToRemove.size(), affectedDays);

        log.debug("Removing affected trips for places [{}]", placesToRemove);
        this.tripJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing affected visits for places [{}]", placesToRemove);
        this.processedVisitJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing places [{}]", placesToRemove);
        this.significantPlaceJdbcService.deleteForUser(user, placesToRemove);
        log.info("Cleanup for geometry change completed in {}ms", (System.nanoTime() - start) / 1000000);

        start = System.nanoTime();
        this.rawLocationPointJdbcService.markAllAsUnprocessedForUser(user, affectedDays);
        log.info("clearing processed points for days [{}] completed in {}ms", affectedDays, (System.nanoTime() - start) / 1000000);

        jobScheduler.enqueueTask(processingPipelineTask,
                                 new ProcessingPipelineTask.TaskData(user.getUsername(), null, null).withParentJobId(parentJobId),
                                  JobSchedulingService.Metadata.builder().jobType(VISIT_TRIP_DETECTION)
                                          .user(user)
                                          .friendlyName("Detect Visits and Trips").build()
        );
    }

    public static class TaskData extends JobContext<TaskData> {

        private final Long userId;
        private final Long placeId;

        public TaskData(Long userId, Long placeId) {
            this(userId, placeId, null, null);
        }

        @JsonCreator
        public TaskData(@JsonProperty("userId") Long userId,
                        @JsonProperty("placeId") Long placeId,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.placeId = placeId;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, placeId, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, placeId, jobId, parentJobId);
        }

    }
}
