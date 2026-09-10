package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SuppressedVisitService {

    private static final Logger logger = LoggerFactory.getLogger(SuppressedVisitService.class);

    private final SuppressedVisitJdbcService suppressedVisitJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final JobSchedulingService jobScheduler;
    private final JobDetail processingPipelineTask;

    public SuppressedVisitService(SuppressedVisitJdbcService suppressedVisitJdbcService,
                                  RawLocationPointJdbcService rawLocationPointJdbcService,
                                  JobSchedulingService jobScheduler,
                                  @Qualifier("processingPipelineJob") JobDetail processingPipelineTask) {
        this.suppressedVisitJdbcService = suppressedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.jobScheduler = jobScheduler;
        this.processingPipelineTask = processingPipelineTask;
    }

    public void suppressVisit(User user, ProcessedVisit visit) {
        logger.info("Suppressing visit [{}] for user [{}] between [{}] and [{}]", visit.getId(), user.getUsername(), visit.getStartTime(), visit.getEndTime());
        SuppressedVisit suppressedVisit = new SuppressedVisit(
                visit.getPlace().getId(),
                visit.getPlace().getLatitudeCentroid(),
                visit.getPlace().getLongitudeCentroid(),
                visit.getStartTime(),
                visit.getEndTime());
        suppressedVisitJdbcService.create(user, suppressedVisit);
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, visit.getStartTime(), visit.getEndTime());
        jobScheduler.enqueueTask(processingPipelineTask,
                                 new ProcessingPipelineTask.TaskData(user.getUsername(), null, null),
                                 JobSchedulingService.Metadata.builder()
                                         .user(user)
                                         .jobType(JobType.MANUAL_MODIFICATION)
                                         .friendlyName("Recalculate visits after manual change")
                                         .build());
    }
}
