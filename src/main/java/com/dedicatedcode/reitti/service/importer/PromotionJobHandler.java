package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupJob;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardProgressBar;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PromotionJobHandler {
    private static final Logger log = LoggerFactory.getLogger(PromotionJobHandler.class);
    private final LocationPointStagingService stagingService;
    private final ImportJobRepository importJobRepository;
    private final JobScheduler jobScheduler;
    private final LocationDataCleanupJob locationDataCleanupJob;

    public PromotionJobHandler(LocationPointStagingService stagingService,
                               ImportJobRepository importJobRepository,
                               JobScheduler jobScheduler,
                               LocationDataCleanupJob locationDataCleanupJob) {
        this.stagingService = stagingService;
        this.importJobRepository = importJobRepository;
        this.jobScheduler = jobScheduler;
        this.locationDataCleanupJob = locationDataCleanupJob;
    }

    @Job(name = "Promote imported points to devices table")
    public void execute(User user, Device device, String partitionKey, boolean dropPartition, JobContext jobContext) {
        JobDashboardProgressBar jobDashboardProgressBar = jobContext.progressBar(3);
        this.importJobRepository.updateState(jobContext.getJobId(), JobState.RUNNING);
        jobDashboardProgressBar.incrementSucceeded();
        TimeRange timeRange = this.stagingService.getTimeRange(partitionKey);
        int promote = this.stagingService.promote(partitionKey);
        jobDashboardProgressBar.incrementSucceeded();
        log.debug("Promoted [{}] points into live table", promote);
        if (dropPartition) {
            this.stagingService.dropPartition(partitionKey);
        }
        jobDashboardProgressBar.incrementSucceeded();
        this.importJobRepository.updateState(jobContext.getJobId(), JobState.COMPLETED);
        if (promote > 0) {
            this.jobScheduler.enqueue(() -> locationDataCleanupJob.execute(user, device, timeRange.start(), timeRange.end(), JobContext.Null));
        } else {
            log.debug("No points to promote, timerange was [{}]", timeRange);
        }
    }
}
