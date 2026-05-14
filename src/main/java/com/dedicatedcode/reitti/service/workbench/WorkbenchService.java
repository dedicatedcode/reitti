package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.dto.workbench.DeletedPointDto;
import com.dedicatedcode.reitti.dto.workbench.EditStoreDto;
import com.dedicatedcode.reitti.dto.workbench.MovedPointDto;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitRequest;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.DeviceTimeRange;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupJob;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class WorkbenchService {
    private static final Logger log = LoggerFactory.getLogger(WorkbenchService.class);
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final Task<LocationDataCleanupJob.TaskData> locationDataCleanupTask;

    public WorkbenchService(SourceLocationPointJdbcService sourceLocationPointJdbcService,
                            DeviceJdbcService deviceJdbcService,
                            JobSchedulingService jobSchedulingService,
                            Task<LocationDataCleanupJob.TaskData> locationDataCleanupTask) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.locationDataCleanupTask = locationDataCleanupTask;
    }

    public void applyCommit(User user, WorkbenchCommitRequest request) {
        log.debug("Applying commit {}", request);
        EditStoreDto editStore = request.getEditStore();

        UUID parentJob = this.jobSchedulingService.createParentJob(user, JobType.MANUAL_MODIFICATION, "Updating data for " + user.getUsername());

        if (editStore.getDeletedPoints() != null) {
            handleDeletion(user, editStore, parentJob);
        }
        if (editStore.getMovedPoints() != null) {
            handleMove(user, editStore, parentJob);
        }
    }

    private void handleMove(User user, EditStoreDto editStore, UUID parentJob) {
        log.debug("Handling move for user [{}] and [{}] points", user.getUsername(), editStore.getMovedPoints().size());
        List<MovedPointDto> movedPoints = editStore.getMovedPoints();
        List<Long> movedPointIds = movedPoints.stream().map(MovedPointDto::getSourceId).toList();
        List<DeviceTimeRange> affectedTimeRange = this.sourceLocationPointJdbcService.findAffectedTimeRange(user, movedPointIds);
        for (MovedPointDto movedPoint : movedPoints) {
            this.sourceLocationPointJdbcService.updateLocation(user, movedPoint.getSourceId(), movedPoint.getLat(), movedPoint.getLng());
        }
        scheduleUpdateJob(user, parentJob, affectedTimeRange);
    }

    private void scheduleUpdateJob(User user, UUID parentJob, List<DeviceTimeRange> affectedTimeRange) {
        for (DeviceTimeRange deviceTimeRange : affectedTimeRange) {
            Device device = deviceTimeRange.deviceId() == null ? null : this.deviceJdbcService.find(user, deviceTimeRange.deviceId()).orElseThrow();
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.LOCATION_DATA_CLEANUP)
                    .friendlyName("Location Data Cleanup")
                    .build();
            this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                  new LocationDataCleanupJob.TaskData(user, device, deviceTimeRange.timeRange().start(), deviceTimeRange.timeRange().end().plus(1, ChronoUnit.MILLIS))
                                                          .withParentJobId(parentJob),
                                                  metadata);
        }
    }

    private void handleDeletion(User user, EditStoreDto editStore, UUID parentJob) {
        log.debug("Handling deletion for user [{}] and [{}] points", user.getUsername(), editStore.getDeletedPoints().size());
        List<DeletedPointDto> deletedPoints = editStore.getDeletedPoints();
        List<Long> deletedPointIds = deletedPoints.stream().map(DeletedPointDto::getSourceId).toList();
        List<DeviceTimeRange> affectedTimeRange = this.sourceLocationPointJdbcService.findAffectedTimeRange(user, deletedPointIds);
        this.sourceLocationPointJdbcService.bulkUpdateManuallyIgnoredStatus(user, deletedPointIds);
        scheduleUpdateJob(user, parentJob, affectedTimeRange);
    }
}
