package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.PreviewRawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@DisallowConcurrentExecution
public class ProcessingPipelineTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(ProcessingPipelineTask.class);

    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final UnifiedLocationProcessingService locationProcessTask;
    private final JobMetadataRepository jobMetadataRepository;
    private final UserProcessingLock userProcessingLock;
    private final BatchFailureTracker batchFailureTracker;
    private final UserNotificationService userNotificationService;
    private final int batchSize;

    public ProcessingPipelineTask(RawLocationPointJdbcService rawLocationPointJdbcService,
                                  PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                                  UserJdbcService userJdbcService,
                                  JobMetadataRepository jobMetadataRepository,
                                  @Value("${reitti.import.batch-size:1000}") int batchSize,
                                  UnifiedLocationProcessingService locationProcessTask,
                                  UserProcessingLock userProcessingLock,
                                  BatchFailureTracker batchFailureTracker,
                                  UserNotificationService userNotificationService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.jobMetadataRepository = jobMetadataRepository;
        this.batchSize = batchSize;
        this.locationProcessTask = locationProcessTask;
        this.userProcessingLock = userProcessingLock;
        this.batchFailureTracker = batchFailureTracker;
        this.userNotificationService = userNotificationService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        execute(TaskData.fromJson((String) dataMap.get("data")));
    }

    public void execute(TaskData event) {
        Optional<User> byUsername = this.userJdbcService.findByUsername(event.getUsername());
        if (byUsername.isPresent()) {
            handleDataForUser(event.getJobId(), byUsername.get(), event.getPreviewId(), event.getTraceId(), event.getParentJobId(), event.isFullReprocess());
        } else {
            log.warn("No user found for username: {}", event.getUsername());
        }
    }

    private void handleDataForUser(UUID jobId, User user, String previewId, String traceId, UUID parentJobId, boolean fullReprocess) {
        userProcessingLock.locked(user, () -> {
            long maxPoints = fullReprocess
                    ? this.rawLocationPointJdbcService.countByUser(user)
                    : this.rawLocationPointJdbcService.countUnprocessedByUser(user);

            AtomicInteger totalProcessed = new AtomicInteger();
            KeysetCursor cursor = new KeysetCursor();

            while (true) {
                List<RawLocationPoint> currentBatch = null;
                Instant earliest = null;
                Instant latest = null;
                try {
                    if (fullReprocess) {
                        currentBatch = rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, cursor.timestamp, batchSize);
                    } else if (previewId == null) {
                        currentBatch = rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, batchSize, 0);
                    } else {
                        currentBatch = previewRawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, previewId, batchSize, 0);
                    }

                    if (currentBatch.isEmpty()) {
                        jobMetadataRepository.updateProgress(jobId, totalProcessed.get(), maxPoints, "Done");
                        break;
                    }

                    earliest = currentBatch.getFirst().getTimestamp();
                    latest = currentBatch.getLast().getTimestamp();
                    log.debug("Scheduling stay detection event for user [{}] and points between [{}] and [{}]", user.getId(), earliest, latest);

                    LocationProcessEvent data = new LocationProcessEvent(user.getUsername(), earliest, latest, previewId, traceId, parentJobId);
                    locationProcessTask.processLocationEvent(data);
                    if (!fullReprocess) {
                        markProcessed(currentBatch, previewId);
                    }
                    cursor.advanceTo(currentBatch);
                    batchFailureTracker.clear(user, earliest);
                    totalProcessed.addAndGet(currentBatch.size());
                    jobMetadataRepository.updateProgress(jobId, totalProcessed.get(), maxPoints, "Processing...");
                } catch (Exception e) {
                    if (earliest != null) {
                        batchFailureTracker.recordFailure(user, earliest);
                    }
                    if (earliest != null && batchFailureTracker.exceedsLimit(user, earliest)) {
                        log.error("Batch for user [{}] between [{}] and [{}] failed [{}] times in a row. Skipping [{}] point(s) to keep the pipeline going. Data in this range may be incomplete.",
                                user.getUsername(), earliest, latest, BatchFailureTracker.MAX_CONSECUTIVE_FAILURES, currentBatch.size(), e);
                        if (!fullReprocess) {
                            markProcessed(currentBatch, previewId);
                        }
                        cursor.advanceTo(currentBatch);
                        batchFailureTracker.clear(user, earliest);
                        totalProcessed.addAndGet(currentBatch.size());
                        jobMetadataRepository.updateProgress(jobId, totalProcessed.get(), maxPoints, "Processing...");
                    } else {
                        log.error("Error processing batch for user [{}] between [{}] and [{}]. Leaving the batch unprocessed and stopping this run.",
                                user.getUsername(), earliest, latest, e);
                        jobMetadataRepository.updateProgress(jobId, totalProcessed.get(), maxPoints, "Failed");
                        break;
                    }
                }
            }
            log.debug("Processed [{}] points for user [{}] (full reprocess: [{}])", totalProcessed.get(), user.getId(), fullReprocess);
        });

        if (previewId != null) {
            this.userNotificationService.previewCompleted(user, previewId);
        }
    }

    /**
     * Resumable (timestamp) keyset cursor, used only in full reprocess mode; in incremental mode
     * the {@code processed} flag is what keeps the next read from returning the rows we just handled.
     */
    private static final class KeysetCursor {
        private Instant timestamp;

        private void advanceTo(List<RawLocationPoint> batch) {
            this.timestamp = batch.getLast().getTimestamp();
        }
    }

    private void markProcessed(List<RawLocationPoint> batch, String previewId) {
        if (previewId == null) {
            rawLocationPointJdbcService.bulkUpdateProcessedStatus(batch);
        } else {
            previewRawLocationPointJdbcService.bulkUpdateProcessedStatus(batch);
        }
    }

    public static class TaskData extends JobContext<TaskData> {
        private final String username;
        private final String previewId;
        private final Instant receivedAt;
        private final String traceId;
        private final boolean fullReprocess;

        public TaskData(
                String username,
                String previewId,
                String traceId) {
            this(username, previewId, traceId, null, null);
        }

        public TaskData(
                String username,
                String previewId,
                String traceId,
                UUID jobId,
                UUID parentJobId) {
            this(username, previewId, traceId, Instant.now(), jobId, parentJobId, false);
        }

        @JsonCreator
        public TaskData(@JsonProperty("username") String username,
                        @JsonProperty("previewId") String previewId,
                        @JsonProperty("traceId") String traceId,
                        @JsonProperty("receivedAt") Instant receivedAt,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId,
                        @JsonProperty("fullReprocess") Boolean fullReprocess) {
            this(username, previewId, traceId, receivedAt, jobId, parentJobId, fullReprocess != null && fullReprocess);
        }

        private TaskData(String username,
                         String previewId,
                         String traceId,
                         Instant receivedAt,
                         UUID jobId,
                         UUID parentJobId,
                         boolean fullReprocess) {
            super(jobId, parentJobId);
            this.username = username;
            this.previewId = previewId;
            this.traceId = traceId;
            this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
            this.fullReprocess = fullReprocess;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        public String getUsername() {
            return username;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public String getPreviewId() {
            return this.previewId;
        }

        public String getTraceId() {
            return traceId;
        }

        public boolean isFullReprocess() {
            return fullReprocess;
        }

        public TaskData withFullReprocess() {
            return new TaskData(username, previewId, traceId, receivedAt, jobId, parentJobId, true);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(username, previewId, traceId, receivedAt, jobId, parentJobId, fullReprocess);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(username, previewId, traceId, receivedAt, jobId, parentJobId, fullReprocess);
        }

        @Override
        public String toString() {
            return "TaskData{" +
                    "username='" + username + '\'' +
                    ", previewId='" + previewId + '\'' +
                    ", receivedAt=" + receivedAt +
                    ", traceId=" + traceId +
                    ", fullReprocess=" + fullReprocess +
                    '}';
        }
    }
}
