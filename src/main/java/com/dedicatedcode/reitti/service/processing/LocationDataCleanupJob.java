package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardProgressBar;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class LocationDataCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(LocationDataCleanupJob.class);
    private final LocationDataDensityNormalizer locationDataDensityNormalizer;
    private final AnomalyProcessingService anomalyProcessingService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserJdbcService userJdbcService;
    private final JobScheduler jobScheduler;
    private final ProcessingPipelineTrigger processingPipelineTrigger;

    public LocationDataCleanupJob(LocationDataDensityNormalizer locationDataDensityNormalizer,
                                  AnomalyProcessingService anomalyProcessingService,
                                  UserSettingsJdbcService userSettingsJdbcService,
                                  UserJdbcService userJdbcService,
                                  JobScheduler jobScheduler,
                                  ProcessingPipelineTrigger processingPipelineTrigger) {
        this.locationDataDensityNormalizer = locationDataDensityNormalizer;
        this.anomalyProcessingService = anomalyProcessingService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userJdbcService = userJdbcService;
        this.jobScheduler = jobScheduler;
        this.processingPipelineTrigger = processingPipelineTrigger;
    }

    @Job(name = "Cleanup incoming data")
    public void execute(User user, Device device, Instant start, Instant end, JobContext jobContext) {
        log.debug("Starting LocationDataCleanupJob for user [{}] and device [{}] between {} and {}", user, device, start, end);
        JobDashboardProgressBar progressBar = jobContext.progressBar(4);
        anomalyProcessingService.processAndMarkAnomalies(user, start, end);
        progressBar.incrementSucceeded();
        locationDataDensityNormalizer.normalize(user, new TimeRange(start, end));
        progressBar.incrementSucceeded();
        userSettingsJdbcService.updateNewestData(user, end);
        progressBar.incrementSucceeded();
        this.userJdbcService.setLastDataModificationAt(user, Instant.now());
        progressBar.incrementSucceeded();
        if (device == null) {
            jobScheduler.schedule(
                    Instant.now().plus(10, ChronoUnit.SECONDS),
                    () -> processingPipelineTrigger.execute(new TriggerProcessingEvent(user.getUsername(), null, null), JobContext.Null)
            );
        }

    }
}
