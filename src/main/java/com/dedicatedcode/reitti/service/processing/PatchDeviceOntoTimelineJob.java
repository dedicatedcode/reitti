package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.TimelineService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PatchDeviceOntoTimelineJob {

    public void execute(TaskData taskData) {

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
