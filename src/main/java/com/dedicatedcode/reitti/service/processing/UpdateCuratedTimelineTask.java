package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.dedicatedcode.reitti.service.jobs.JobType.VISIT_TRIP_DETECTION;

@Service
public class UpdateCuratedTimelineTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(UpdateCuratedTimelineTask.class);

    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final SyntheticPointInserter syntheticPointInserter;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail processingPipelineTask;

    public UpdateCuratedTimelineTask(RawLocationPointJdbcService rawLocationPointJdbcService,
                                     SyntheticPointInserter syntheticPointInserter,
                                     JobSchedulingService jobSchedulingService,
                                     @Qualifier("processingPipelineJob") JobDetail processingPipelineTask) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.syntheticPointInserter = syntheticPointInserter;
        this.jobSchedulingService = jobSchedulingService;
        this.processingPipelineTask = processingPipelineTask;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        TaskData data = (TaskData) dataMap.get("data");
        execute(data);
    }
    public void execute(TaskData data) {
        log.debug("Starting updating main timeline for user [{}] and device[{}] in timeRange [{}]", data.user, data.device, data.timeRange);
        //1. clear main timeline
        this.rawLocationPointJdbcService.dropForReSeeding(data.user, data.timeRange);
        //2. update main timeline from view
        int updatedCount = this.rawLocationPointJdbcService.updateFromDevices(data.user, data.timeRange);
        log.debug("Updated {} timeline points for user [{}] and device[{}] in timeRange [{}]", updatedCount, data.user, data.device, data.timeRange);
        //3. insert new possible synthetic points
        this.syntheticPointInserter.fillGaps(data.user, data.timeRange);
        //4. trigger new processing job
        this.jobSchedulingService.enqueueTask(processingPipelineTask,
                                 new ProcessingPipelineTask.TaskData(data.user.getUsername(), null, null),
                                              JobSchedulingService.Metadata.builder().jobType(VISIT_TRIP_DETECTION)
                                                      .user(data.user)
                                                      .friendlyName("Detect Visits and Trips").build());

    }

    public static class TaskData extends JobContext<TaskData> {

        private final User user;
        private final Device device;
        private final TimeRange timeRange;

        public TaskData(User user, Device device, TimeRange timeRange) {
            this(user, device, timeRange, null, null);
        }

        public TaskData(User user, Device device, TimeRange timeRange, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.device = device;
            this.timeRange = timeRange;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, device, timeRange, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, device, timeRange, jobId, parentJobId);
        }
    }
}
