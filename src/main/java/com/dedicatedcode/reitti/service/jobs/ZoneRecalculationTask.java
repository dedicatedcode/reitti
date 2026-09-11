package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Service
@DisallowConcurrentExecution
public class ZoneRecalculationTask implements Job {

    private static final Logger log = LoggerFactory.getLogger(ZoneRecalculationTask.class);

    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final JobSchedulingService jobScheduler;
    private final JobMetadataRepository jobMetadataRepository;
    private final JobDetail processingPipelineTask;

    public ZoneRecalculationTask(RawLocationPointJdbcService rawLocationPointJdbcService,
                                 JobSchedulingService jobScheduler,
                                 JobMetadataRepository jobMetadataRepository,
                                 @Qualifier("processingPipelineJob") JobDetail processingPipelineTask) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.jobScheduler = jobScheduler;
        this.jobMetadataRepository = jobMetadataRepository;
        this.processingPipelineTask = processingPipelineTask;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");
        execute(data);
    }

    public void execute(TaskData data) {
        User user = data.user;
        UUID jobId = data.getJobId();

        this.jobMetadataRepository.updateProgress(jobId, 0, 2, "Marking raw location points inside the zone as unprocessed ...");
        rawLocationPointJdbcService.markUnprocessedForUserAndBoundingBox(user, data.minLatitude, data.maxLatitude, data.minLongitude, data.maxLongitude);
        log.info("Marked raw location points for user [{}] inside the zone bounding box as unprocessed", user.getUsername());

        this.jobMetadataRepository.updateProgress(jobId, 1, 2, "Starting visit and trip detection ...");
        jobScheduler.enqueueTask(processingPipelineTask,
                                 new ProcessingPipelineTask.TaskData(user.getUsername(), null, null).withParentJobId(jobId),
                                 JobSchedulingService.Metadata.builder()
                                         .user(user)
                                         .jobType(VISIT_TRIP_DETECTION)
                                         .friendlyName("Detect Visits and Trips")
                                         .build());

        this.jobMetadataRepository.updateProgress(jobId, 2, 2, "Done");
    }

    public static class TaskData extends JobContext<TaskData> {

        private final User user;
        private final double minLatitude;
        private final double maxLatitude;
        private final double minLongitude;
        private final double maxLongitude;

        public TaskData(User user, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
            this(user, minLatitude, maxLatitude, minLongitude, maxLongitude, null, null);
        }

        public TaskData(User user, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.minLatitude = minLatitude;
            this.maxLatitude = maxLatitude;
            this.minLongitude = minLongitude;
            this.maxLongitude = maxLongitude;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, minLatitude, maxLatitude, minLongitude, maxLongitude, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, minLatitude, maxLatitude, minLongitude, maxLongitude, jobId, parentJobId);
        }
    }
}
