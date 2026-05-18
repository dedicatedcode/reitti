package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.workbench.TimelineOverrideService;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PatchDeviceOntoTimelineJob {
    private static final Logger log = LoggerFactory.getLogger(PatchDeviceOntoTimelineJob.class);

    private final TimelineOverrideService timelineOverrideService;
    private final JobSchedulingService jobSchedulingService;
    private final Task<UpdateCuratedTimelineJob.TaskData> updateCuratedTimelineTask;
    private final I18nService i18n;
    public PatchDeviceOntoTimelineJob(TimelineOverrideService timelineOverrideService,
                                      JobSchedulingService jobSchedulingService,
                                      Task<UpdateCuratedTimelineJob.TaskData> updateCuratedTimelineTask,
                                      I18nService i18n) {
        this.timelineOverrideService = timelineOverrideService;
        this.jobSchedulingService = jobSchedulingService;
        this.updateCuratedTimelineTask = updateCuratedTimelineTask;
        this.i18n = i18n;
    }

    public void execute(TaskData taskData) {
        log.debug("Updating timeline override for user [{}], device [{}] between [{}] and [{}]", taskData.user, taskData.device, taskData.start, taskData.end);
        this.timelineOverrideService.setTimelineOverride(taskData.user, taskData.device, taskData.start, taskData.end);
        log.info("Updated timeline override for user [{}], device [{}] between [{}] and [{}]", taskData.user, taskData.device, taskData.start, taskData.end);
        this.jobSchedulingService.enqueueTask(updateCuratedTimelineTask,
                                              new UpdateCuratedTimelineJob.TaskData(taskData.user, taskData.device, TimeRange.of(taskData.start, taskData.end))
                                                      .withParentJobId(taskData.getJobId()), JobSchedulingService.Metadata.builder()
                                                      .user(taskData.user)
                                                      .friendlyName(i18n.translate("jobs.recalculate_timeline.stitching.friendly_name", taskData.start, taskData.end))
                                                      .jobType(JobType.TIMELINE_STITCHING).build());
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final User user;
        private final Device device;
        private final Instant start;
        private final Instant end;

        public TaskData(User user, Device device, Instant start, Instant end) {
            this(user, device, start, end, null, null);
        }
        public TaskData(User user, Device device, Instant start, Instant end, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.device = device;
            this.start = start;
            this.end = end;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, device, start, end, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, device, start, end, jobId, parentJobId);
        }
    }
}
