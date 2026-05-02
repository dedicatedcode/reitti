package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.task.Task;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GoogleIOSTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromIOS() {
        JobSchedulingService jobScheduler = mock(JobSchedulingService.class);

        GoogleIOSTimelineImporter importHandler = new GoogleIOSTimelineImporter(new ObjectMapper(), new ImportStateHolder(),
                                                                                mock(LocationPointStagingService.class),
                                                                                mock(Task.class),
                                                                                jobScheduler,
                                                                                0);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_ios_randomized.json"), user, null, "timeline_from_ios_randomized.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Verify that jobScheduler.enqueue was called since graceTimeSeconds is 0
        verify(jobScheduler, times(1)).scheduleTask(any(Task.class), any(PromotionJobHandler.PromotionTaskData.class), any(Instant.class), any(JobSchedulingService.Metadata.class));
    }
}