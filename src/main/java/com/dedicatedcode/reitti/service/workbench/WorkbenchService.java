package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.dto.workbench.*;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.DeviceTimeRange;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.PatchDeviceOntoTimelineTask;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class WorkbenchService {
    private static final Logger log = LoggerFactory.getLogger(WorkbenchService.class);
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail locationDataCleanupTask;
    private final JobDetail patchDeviceOntoTimelineTask;
    private final I18nService i18n;

    public WorkbenchService(SourceLocationPointJdbcService sourceLocationPointJdbcService,
                            DeviceJdbcService deviceJdbcService,
                            JobSchedulingService jobSchedulingService,
                            @Qualifier("locationDataCleanupJob") JobDetail locationDataCleanupTask,
                            @Qualifier("patchDeviceOntoTimelineJob") JobDetail patchDeviceOntoTimelineTask,
                            I18nService i18n) {
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.locationDataCleanupTask = locationDataCleanupTask;
        this.patchDeviceOntoTimelineTask = patchDeviceOntoTimelineTask;
        this.i18n = i18n;
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
        if (editStore.getPatches() != null) {
            handlePatches(user, editStore.getPatches(), parentJob);
        }
    }

    private void handlePatches(User user, List<PatchDto> patches, UUID parentJob) {
        log.debug("Handling patches for user [{}] and [{}] patches", user.getUsername(), patches.size());
        patches.stream().sorted(Comparator.comparing(PatchDto::getSeq))
                .forEachOrdered(patchDto -> {
                    Device device = this.deviceJdbcService.find(user, Long.valueOf(patchDto.getDeviceId())).orElseThrow();
                    Instant start = Instant.ofEpochMilli(patchDto.gettStart());
                    Instant end = Instant.ofEpochMilli(patchDto.gettEnd());
                    JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                            .user(user)
                            .jobType(JobType.TIMELINE_STITCHING)
                            .friendlyName(i18n.translate("jobs.timeline_stitching.friendly_name", device.name(), start, end)).build();
                    this.jobSchedulingService.enqueueTask(patchDeviceOntoTimelineTask,
                                                          new PatchDeviceOntoTimelineTask.TaskData(user, device, start, end).withParentJobId(parentJob),
                                                          metadata);
                });
    }

    private void handleDeletion(User user, EditStoreDto editStore, UUID parentJob) {
        log.debug("Handling deletion for user [{}] and [{}] points", user.getUsername(), editStore.getDeletedPoints().size());
        List<DeletedPointDto> deletedPoints = editStore.getDeletedPoints();
        List<Long> deletedPointIds = deletedPoints.stream().map(DeletedPointDto::getSourceId).toList();
        List<DeviceTimeRange> affectedTimeRange = this.sourceLocationPointJdbcService.findAffectedTimeRange(user, deletedPointIds);
        this.sourceLocationPointJdbcService.bulkUpdateManuallyIgnoredStatus(user, deletedPointIds);
        scheduleUpdateJob(user, parentJob, affectedTimeRange);
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
            Device device = this.deviceJdbcService.find(user, deviceTimeRange.deviceId()).orElseThrow();
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.LOCATION_DATA_CLEANUP)
                    .friendlyName("Location Data Cleanup")
                    .build();
            this.jobSchedulingService.enqueueTask(locationDataCleanupTask,
                                                  new LocationDataCleanupTask.TaskData(user, device, deviceTimeRange.timeRange().start(), deviceTimeRange.timeRange().end().plus(1, ChronoUnit.MILLIS))
                                                          .withParentJobId(parentJob),
                                                  metadata);
        }
    }

}
