package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Imports a slice of the user's intervals.icu history and reschedules itself until the whole history is
 * covered. Each execution imports at most reitti.imports.intervals-icu.max-activities-per-run activities so
 * that no single Quartz thread stays occupied for hours, and the delay between slices keeps us below the
 * 10 requests per second per IP limit intervals.icu enforces.
 *
 * The job is resumable: every imported activity is recorded in intervals_icu_imported_activities, so a restart
 * in the middle of a long import continues where it left off instead of downloading anything twice.
 */
@Service
@DisallowConcurrentExecution
public class IntervalsIcuHistoricalImportTask implements Job {

    private static final Logger logger = LoggerFactory.getLogger(IntervalsIcuHistoricalImportTask.class);

    private final IntervalsIcuIntegrationService integrationService;
    private final UserJdbcService userJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository jobMetadataRepository;

    public IntervalsIcuHistoricalImportTask(IntervalsIcuIntegrationService integrationService,
                                            UserJdbcService userJdbcService,
                                            JobSchedulingService jobSchedulingService,
                                            JobMetadataRepository jobMetadataRepository) {
        this.integrationService = integrationService;
        this.userJdbcService = userJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = TaskData.fromJson((String) context.getMergedJobDataMap().get("data"));
        UUID jobId = data.getJobId();

        User user = this.userJdbcService.findById(data.userId()).orElse(null);
        if (user == null) {
            logger.warn("User {} no longer exists, dropping the intervals.icu historical import", data.userId());
            return;
        }

        IntervalsIcuIntegrationService.SyncResult slice;
        try {
            slice = this.integrationService.importHistoricalSlice(user, data.fromDate(), data.toDate());
        } catch (Exception e) {
            String reason = describe(e);
            // job_meta_data has no dedicated error column, so the reason is parked in progress_message and
            // rendered by the job status page. Otherwise a failed import only ever says "FAILED".
            this.jobMetadataRepository.updateProgress(jobId, 0, 0, reason);
            logger.error("Failed to import a slice of intervals.icu history for user {}: {}", user.getUsername(), reason, e);
            throw new JobExecutionException(reason, e, false);
        }

        int done = slice.imported() + slice.skipped() + slice.failed();
        this.jobMetadataRepository.updateProgress(jobId, done, done + slice.remaining(),
                "Imported %d activities, %d remaining".formatted(done, slice.remaining()));

        if (slice.remaining() <= 0) {
            logger.info("intervals.icu historical import finished for user {}: {} imported, {} skipped, {} failed",
                    user.getUsername(), slice.imported(), slice.skipped(), slice.failed());
            return;
        }

        if (slice.resumeFrom() == null) {
            // Without a usable resume point we would re-fetch the same window forever.
            logger.warn("Cannot resume the intervals.icu historical import for user {}, stopping with {} activities remaining",
                    user.getUsername(), slice.remaining());
            return;
        }

        TaskData next = data.withFromDate(slice.resumeFrom().toString());
        Duration delay = this.integrationService.getSliceDelay();

        // defer() rebuilds the trigger from the job data map of the running execution, so the advanced
        // cursor has to be written back before deferring. Without this the next slice would start over.
        context.getMergedJobDataMap().put("data", next.toJson());

        if (!this.jobSchedulingService.defer(context, delay, "%d activities remaining".formatted(slice.remaining()))) {
            logger.warn("Cannot defer the intervals.icu historical import for user {}, stopping", user.getUsername());
            return;
        }
        logger.debug("Resuming the intervals.icu historical import for user {} from {} in {}s",
                user.getUsername(), next.fromDate(), delay.toSeconds());
    }

    /**
     * Turns an exception into something a user can act on. Raw messages are unhelpful here, for example
     * "Error while extracting response for type [List&lt;...ActivitySummary&gt;]" or a bare URL.
     */
    private String describe(Exception e) {
        String message = switch (e) {
            case HttpStatusCodeException status -> "intervals.icu rejected the request: %d %s"
                    .formatted(status.getStatusCode().value(), status.getStatusText());
            case ResourceAccessException unreachable -> "Could not reach intervals.icu: %s"
                    .formatted(rootMessage(unreachable));
            case IllegalStateException unexpected -> unexpected.getMessage();
            default -> e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        };
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    public static class TaskData extends JobContext<TaskData> {

        private final Long userId;
        private final String fromDate;
        private final String toDate;

        public TaskData(Long userId, LocalDate fromDate, LocalDate toDate) {
            this(userId, fromDate.toString(), toDate.toString());
        }

        private TaskData(Long userId, String fromDate, String toDate) {
            this.userId = userId;
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        @JsonCreator
        private TaskData(@JsonProperty("jobId") UUID jobId,
                         @JsonProperty("parentJobId") UUID parentJobId,
                         @JsonProperty("userId") Long userId,
                         @JsonProperty("fromDate") String fromDate,
                         @JsonProperty("toDate") String toDate) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        public Long userId() {
            return userId;
        }

        public LocalDate fromDate() {
            return LocalDate.parse(fromDate);
        }

        public LocalDate toDate() {
            return LocalDate.parse(toDate);
        }

        public TaskData withFromDate(String fromDate) {            return new TaskData(jobId, parentJobId, userId, fromDate, this.toDate);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, userId, fromDate, toDate);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, userId, fromDate, toDate);
        }
    }
}
