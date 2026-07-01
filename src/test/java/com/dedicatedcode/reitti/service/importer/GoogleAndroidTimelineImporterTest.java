package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GoogleAndroidTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromAndroid() {
        // Mock JobScheduler to verify scheduling behavior
        JobSchedulingService jobScheduler = mock(JobSchedulingService.class);

        GoogleAndroidTimelineImporter importHandler = new GoogleAndroidTimelineImporter(new ObjectMapper(),
                                                                                        new ImportStateHolder(),
                                                                                        mock(LocationPointStagingService.class),
                                                                                        mock(JobDetail.class),
                                                                                        jobScheduler,
                                                                                        0);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_android_randomized.json"), user, mock(Device.class), "timeline_from_android_randomized.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Verify that jobScheduler.enqueue was called since graceTimeSeconds is 0
        verify(jobScheduler, times(1)).scheduleTask(any(JobDetail.class), any(PromotionJobHandler.TaskData.class), any(Instant.class), any(JobSchedulingService.Metadata.class));
    }
}