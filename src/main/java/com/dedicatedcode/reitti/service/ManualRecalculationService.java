package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.jobs.ZoneRecalculationTask;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ManualRecalculationService {

    private final JobSchedulingService jobScheduler;
    private final JobDetail processingPipelineTask;
    private final JobDetail zoneRecalculationTask;

    public ManualRecalculationService(JobSchedulingService jobScheduler,
                                      @Qualifier("processingPipelineJob") JobDetail processingPipelineTask,
                                      @Qualifier("zoneRecalculationJob") JobDetail zoneRecalculationTask) {
        this.jobScheduler = jobScheduler;
        this.processingPipelineTask = processingPipelineTask;
        this.zoneRecalculationTask = zoneRecalculationTask;
    }

    public void schedule(User user) {
        schedule(user, "Recalculate visits after manual change");
    }

    public void schedule(User user, String friendlyName) {
        jobScheduler.enqueueTask(processingPipelineTask,
                                 new ProcessingPipelineTask.TaskData(user.getUsername(), null, null),
                                 JobSchedulingService.Metadata.builder()
                                         .user(user)
                                         .jobType(JobType.MANUAL_MODIFICATION)
                                         .friendlyName(friendlyName)
                                         .build());
    }

    public void scheduleZoneArea(User user, List<GeoPoint> polygon, String friendlyName) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;
        for (GeoPoint point : polygon) {
            minLat = Math.min(minLat, point.latitude());
            maxLat = Math.max(maxLat, point.latitude());
            minLng = Math.min(minLng, point.longitude());
            maxLng = Math.max(maxLng, point.longitude());
        }
        jobScheduler.enqueueTask(zoneRecalculationTask,
                                 new ZoneRecalculationTask.TaskData(user, minLat, maxLat, minLng, maxLng),
                                 JobSchedulingService.Metadata.builder()
                                         .user(user)
                                         .jobType(JobType.MANUAL_MODIFICATION)
                                         .friendlyName(friendlyName)
                                         .build());
    }
}
