package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GoogleIOSTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromIOS() {
        JobSchedulingService jobScheduler = mock(JobSchedulingService.class);

        GoogleIOSTimelineImporter importHandler = new GoogleIOSTimelineImporter(new ObjectMapper(),
                                                                                mock(LocationPointStagingService.class),
                                                                                mock(JobDetail.class),
                                                                                jobScheduler,
                                                                                0);
        User user = new User("test", "Test User");
        Device device = new Device(3L, "phone", true, true, true, "#ffffff", true, Instant.now(), Instant.now(), 0L);
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_ios_randomized.json"), user, device, "timeline_from_ios_randomized.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Verify that jobScheduler.enqueue was called since graceTimeSeconds is 0
        verify(jobScheduler, times(1)).scheduleTask(any(JobDetail.class), any(PromotionJobHandler.TaskData.class), any(Instant.class), any(JobSchedulingService.Metadata.class));
    }
}