package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JobMetadataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(JobMetadataCleanupService.class);

    private final JobMetadataRepository jobMetadataRepository;
    private final int maxAgeHours;

    public JobMetadataCleanupService(JobMetadataRepository jobMetadataRepository,
                                     @Value("${reitti.jobs.cleanup.max-age-hours:72}") int maxAgeHours) {
        this.jobMetadataRepository = jobMetadataRepository;
        this.maxAgeHours = maxAgeHours;
    }

    @Scheduled(cron = "${reitti.jobs.cleanup.cron:0 0 4 * * ?}")
    @Transactional
    public void cleanUpOldJobs() {
        Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
        int deleted = jobMetadataRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up {} job metadata entries older than {} hours", deleted, maxAgeHours);
    }
}