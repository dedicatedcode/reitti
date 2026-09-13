package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.workbench.TimelineOverrideService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@DisallowConcurrentExecution
public class PatchDeviceOntoTimelineTask implements Job {
    private static final Logger log = LoggerFactory.getLogger(PatchDeviceOntoTimelineTask.class);

    private final TimelineOverrideService timelineOverrideService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail updateCuratedTimelineTask;
    private final I18nService i18n;
    public PatchDeviceOntoTimelineTask(TimelineOverrideService timelineOverrideService,
                                       UserJdbcService userJdbcService,
                                       DeviceJdbcService deviceJdbcService,
                                       JobSchedulingService jobSchedulingService,
                                       @Qualifier("updateCuratedTimelineJob") JobDetail updateCuratedTimelineTask,
                                       I18nService i18n) {
        this.timelineOverrideService = timelineOverrideService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.jobSchedulingService = jobSchedulingService;
        this.updateCuratedTimelineTask = updateCuratedTimelineTask;
        this.i18n = i18n;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        execute(TaskData.fromJson((String) context.getMergedJobDataMap().get("data")));
    }

    public void execute(TaskData taskData) {
        User user = userJdbcService.findById(taskData.userId).orElseThrow(() -> new IllegalArgumentException("User with id [" + taskData.userId + "] not found"));
        Device device = deviceJdbcService.find(user, taskData.deviceId).orElseThrow(() -> new IllegalArgumentException("Device with id [" + taskData.deviceId + "] not found for user [" + user.getUsername() + "]"));
        log.debug("Updating timeline override for user [{}], device [{}] between [{}] and [{}]", user, device, taskData.start, taskData.end);
        this.timelineOverrideService.setTimelineOverride(user, device, taskData.start, taskData.end);
        log.info("Updated timeline override for user [{}], device [{}] between [{}] and [{}]", user, device, taskData.start, taskData.end);
        this.jobSchedulingService.enqueueTask(updateCuratedTimelineTask,
                                              new UpdateCuratedTimelineTask.TaskData(user.getId(), device.id(), TimeRange.of(taskData.start, taskData.end))
                                                      .withParentJobId(taskData.getParentJobId()), JobSchedulingService.Metadata.builder()
                                                      .user(user)
                                                      .friendlyName(i18n.translate("jobs.recalculate_timeline.stitching.friendly_name", taskData.start, taskData.end))
                                                      .jobType(JobType.TIMELINE_STITCHING).build());
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final Long userId;
        private final Long deviceId;
        private final Instant start;
        private final Instant end;

        public TaskData(Long userId, Long deviceId, Instant start, Instant end) {
            this(userId, deviceId, start, end, null, null);
        }
        @JsonCreator
        public TaskData(@JsonProperty("userId") Long userId,
                        @JsonProperty("deviceId") Long deviceId,
                        @JsonProperty("start") Instant start,
                        @JsonProperty("end") Instant end,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.userId = userId;
            this.deviceId = deviceId;
            this.start = start;
            this.end = end;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(userId, deviceId, start, end, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(userId, deviceId, start, end, jobId, parentJobId);
        }
    }
}
