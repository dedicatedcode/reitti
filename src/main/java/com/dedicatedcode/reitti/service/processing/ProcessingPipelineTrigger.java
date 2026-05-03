package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.PreviewRawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessingPipelineTrigger {
    private static final Logger log = LoggerFactory.getLogger(ProcessingPipelineTrigger.class);

    private final ImportStateHolder stateHolder;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final UnifiedLocationProcessingService locationProcessTask;
    private final JobMetadataRepository jobMetadataRepository;
    private final int batchSize;

    public ProcessingPipelineTrigger(ImportStateHolder stateHolder,
                                     RawLocationPointJdbcService rawLocationPointJdbcService,
                                     PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                                     UserJdbcService userJdbcService,
                                     JobMetadataRepository jobMetadataRepository,
                                     @Value("${reitti.import.batch-size:100}") int batchSize,
                                     UnifiedLocationProcessingService locationProcessTask) {
        this.stateHolder = stateHolder;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.jobMetadataRepository = jobMetadataRepository;
        this.batchSize = batchSize;
        this.locationProcessTask = locationProcessTask;
    }

    public void execute(TriggerProcessingEvent event) {
        Optional<User> byUsername = this.userJdbcService.findByUsername(event.getUsername());
        if (byUsername.isPresent()) {
            handleDataForUser(event.getJobId(), byUsername.get(), event.getPreviewId(), event.getTraceId(), event.getParentJobId());
        } else {
            log.warn("No user found for username: {}", event.getUsername());
        }
    }

    private void handleDataForUser(UUID jobId, User user, String previewId, String traceId, UUID parentJobId) {
        int totalProcessed = 0;

        long maxPoints = this.rawLocationPointJdbcService.countUnprocessedByUser(user);
        while (true) {
            stateHolder.importStarted();
            try {
                List<RawLocationPoint> currentBatch;
                if (previewId == null) {
                    currentBatch = rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, batchSize, 0);
                } else {
                    currentBatch = previewRawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, previewId, batchSize, 0);
                }

                if (currentBatch.isEmpty()) {
                    jobMetadataRepository.updateProgress(jobId, totalProcessed, maxPoints, "Done");
                    break;
                }

                Instant earliest = currentBatch.getFirst().getTimestamp();
                Instant latest = currentBatch.getLast().getTimestamp();
                log.debug("Scheduling stay detection event for user [{}] and points between [{}] and [{}]", user.getId(), earliest, latest);
                if (previewId == null) {
                    rawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
                } else {
                    previewRawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
                }

                LocationProcessEvent data = new LocationProcessEvent(user.getUsername(), earliest, latest, previewId, traceId, parentJobId);
                locationProcessTask.processLocationEvent(data);
                totalProcessed += currentBatch.size();
                jobMetadataRepository.updateProgress(jobId,totalProcessed, maxPoints, "Processing...");
            } catch (Exception e) {
                log.error("Error processing batch for user [{}]", user.getId(), e);
            }
        }
        stateHolder.importFinished();
        log.debug("Processed [{}] unprocessed points for user [{}]", totalProcessed, user.getId());
    }
}
