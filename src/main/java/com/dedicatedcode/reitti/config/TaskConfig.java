package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.service.UserSseEmitterService;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.importer.PromotionJobHandler;
import com.dedicatedcode.reitti.service.jobs.VisitSensitivityConfigurationRecalculationTask;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupJob;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class TaskConfig {
    @Bean
    public Task<UserSseEmitterService.TaskData> sseEmitterTask(UserSseEmitterService userSseEmitterService) {
        return Tasks.oneTime("sse-emitter-task", UserSseEmitterService.TaskData.class)
                .execute((instance, context) -> {
                    UserSseEmitterService.TaskData data = instance.getData();
                    userSseEmitterService.sendEventToUser(data.user(), data.eventData());
                });
    }

    @Bean
    public Task<SignificantPlaceCreatedEvent> significantPlaceCreatedTask(ReverseGeocodingListener reverseGeocodingListener) {
        return Tasks.oneTime("reverse-geocoding-task", SignificantPlaceCreatedEvent.class)
                .execute((instance, context) -> {
                    SignificantPlaceCreatedEvent data = instance.getData();
                    reverseGeocodingListener.handleSignificantPlaceCreated(data);
                });
    }

    @Bean
    public Task<TriggerProcessingEvent> processingPipelineTask(ProcessingPipelineTrigger handler) {
        return Tasks.oneTime("processing-pipeline-task", TriggerProcessingEvent.class)
                .execute((instance, context) -> {
                    TriggerProcessingEvent data = instance.getData();
                    UUID jobId = UUID.fromString(instance.getId());
                    handler.execute(jobId, data);
                });
    }

    @Bean
    public Task<LocationDataCleanupJob.TaskData> locationDataCleanupTask(LocationDataCleanupJob handler) {
        return Tasks.oneTime("location-data-cleanup-task", LocationDataCleanupJob.TaskData.class)
                .execute((instance, context) -> {
                    handler.execute(instance.getData());
                });
    }

    @Bean
    public Task<PromotionJobHandler.PromotionTaskData> promotionTask(PromotionJobHandler handler) {
        return Tasks.oneTime("promotion-task", PromotionJobHandler.PromotionTaskData.class)
                .execute((instance, context) -> {
                    PromotionJobHandler.PromotionTaskData data = instance.getData();
                    handler.execute(
                            UUID.fromString(instance.getId()),
                            data.getUser(),
                            data.getDevice(),
                            data.getPartitionKey(),
                            data.getParentJobId(),
                            data.isManual()
                    );
                });
    }

    @Bean
    public Task<VisitSensitivityConfigurationRecalculationTask.TaskData> dataRecalculationTask(VisitSensitivityConfigurationRecalculationTask handler) {
        return Tasks.oneTime("data-recalculation-task", VisitSensitivityConfigurationRecalculationTask.TaskData.class)
                .execute((instance, context) -> {
                    VisitSensitivityConfigurationRecalculationTask.TaskData data = instance.getData();
                    handler.execute(data);
                });
    }
}
