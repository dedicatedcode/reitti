package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.service.DataCleanupService;
import com.dedicatedcode.reitti.service.UserSseEmitterService;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.importer.PromotionJobHandler;
import com.dedicatedcode.reitti.service.jobs.TransportModeRecalculationTask;
import com.dedicatedcode.reitti.service.jobs.VisitSensitivityConfigurationRecalculationTask;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.PatchDeviceOntoTimelineTask;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import com.dedicatedcode.reitti.service.processing.UpdateCuratedTimelineTask;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfig {

    @Bean("patchDeviceOntoTimelineJob")
    public JobDetail patchDeviceOntoTimelineJobDetail() {
        return JobBuilder.newJob(PatchDeviceOntoTimelineTask.class)
                .withIdentity("patch-device-onto-timeline-job")
                .storeDurably()
                .build();
    }

    @Bean("userSSEEmitterJob")
    public JobDetail sseEmitterJobDetail() {
        return JobBuilder.newJob(UserSseEmitterService.class)
                .withIdentity("sse-emitter-job")
                .storeDurably()
                .build();
    }

    @Bean("reverseGeocodingJob")
    public JobDetail significantPlaceCreatedJobDetail() {
        return JobBuilder.newJob(ReverseGeocodingListener.class)
                .withIdentity("reverse-geocoding-job")
                .storeDurably()
                .build();
    }

    @Bean("updateCuratedTimelineJob")
    public JobDetail updateCuratedTimelineJobDetail() {
        return JobBuilder.newJob(UpdateCuratedTimelineTask.class)
                .withIdentity("updating-curated-timeline-job")
                .storeDurably()
                .build();
    }

    @Bean("processingPipelineJob")
    public JobDetail processingPipelineJobDetail() {
        return JobBuilder.newJob(ProcessingPipelineTask.class)
                .withIdentity("processing-pipeline-job")
                .storeDurably()
                .build();
    }

    @Bean("locationDataCleanupJob")
    public JobDetail locationDataCleanupJobDetail() {
        return JobBuilder.newJob(LocationDataCleanupTask.class)
                .withIdentity("location-data-cleanup-job")
                .storeDurably()
                .build();
    }

    @Bean("promotionJob")
    public JobDetail promotionJobDetail() {
        return JobBuilder.newJob(PromotionJobHandler.class)
                .withIdentity("promotion-job")
                .storeDurably()
                .build();
    }

    @Bean("polygonUpdateJob")
    public JobDetail polygonUpdateJobDetail() {
        return JobBuilder.newJob(DataCleanupService.class)
                .withIdentity("polygon-update-job")
                .storeDurably()
                .build();
    }

    @Bean("visitSensitivityRecalculationJob")
    public JobDetail dataRecalculationJobDetail() {
        return JobBuilder.newJob(VisitSensitivityConfigurationRecalculationTask.class)
                .withIdentity("data-recalculation-job")
                .storeDurably()
                .build();
    }

    @Bean("transportModeRecalculationJob")
    public JobDetail transportModeRecalculationJobDetail() {
        return JobBuilder.newJob(TransportModeRecalculationTask.class)
                .withIdentity("transport-mode-recalculation-job")
                .storeDurably()
                .build();
    }
}
