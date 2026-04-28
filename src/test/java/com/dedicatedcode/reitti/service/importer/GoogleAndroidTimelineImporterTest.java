package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleAndroidTimelineImporterTest {

    @Test
    void shouldParseNewGoogleTakeOutFileFromAndroid() {
        // Mock JobScheduler to verify scheduling behavior
        JobScheduler jobScheduler = mock(JobScheduler.class);

        GoogleAndroidTimelineImporter importHandler = new GoogleAndroidTimelineImporter(new ObjectMapper(),
                                                                                        new ImportStateHolder(),
                                                                                        mock(LocationPointStagingService.class),
                                                                                        mock(ImportJobRepository.class),
                                                                                        mock(PromotionJobHandler.class),
                                                                                        jobScheduler,
                                                                                        0);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_android_randomized.json"), user, null, "timeline_from_android_randomized.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Verify that jobScheduler.enqueue was called since graceTimeSeconds is 0
        verify(jobScheduler, times(1)).enqueue(any(UUID.class), any(JobLambda.class));
    }
}