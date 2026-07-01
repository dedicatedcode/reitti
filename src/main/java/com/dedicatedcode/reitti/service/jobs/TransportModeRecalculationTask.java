package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.processing.TransportModeService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TransportModeRecalculationTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(TransportModeRecalculationTask.class);
    private final TripJdbcService tripJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final TransportModeService transportModeService;
    private final JobMetadataRepository metadataRepository;

    public TransportModeRecalculationTask(TripJdbcService tripJdbcService, RawLocationPointJdbcService rawLocationPointJdbcService, TransportModeService transportModeService, JobMetadataRepository metadataRepository) {
        this.tripJdbcService = tripJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.transportModeService = transportModeService;
        this.metadataRepository = metadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        TaskData data = (TaskData) dataMap.get("data");
        execute(data);
    }

    public void execute(TaskData taskData) {
        User user = taskData.user;
        long allTripsAmountForUser = this.tripJdbcService.count(taskData.user);
        metadataRepository.updateProgress(taskData.getJobId(), 0, allTripsAmountForUser, "Updating trips");
        AtomicLong currentTrip = new AtomicLong();
        tripJdbcService.findByUser(user).forEach(trip -> {
            Instant startTime = trip.getStartTime();
            Instant endTime = trip.getEndTime();
            List<RawLocationPoint> tripPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startTime, endTime.plus(1, ChronoUnit.MILLIS));
            TransportMode transportMode = this.transportModeService.inferTransportMode(user, tripPoints, startTime, endTime);
            if (transportMode != trip.getTransportModeInferred()) {
                log.trace("Reclassified trip {} from {} to {} to mode {}", trip.getId(), startTime, endTime, transportMode);
                trip = trip.withTransportMode(transportMode);
                this.tripJdbcService.update(trip);
            }
            if (currentTrip.getAndIncrement() % 100 == 0) {
                metadataRepository.updateProgress(taskData.getJobId(), currentTrip.get(), allTripsAmountForUser, "Updating trips");
            }
        });
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
