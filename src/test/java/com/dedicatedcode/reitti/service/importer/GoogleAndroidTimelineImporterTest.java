package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

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
                                                                                        mock(JobMetadataRepository.class),
                                                                                        mock(PromotionJobHandler.class),
                                                                                        jobScheduler,
                                                                                        0);
        User user = new User("test", "Test User");
        Map<String, Object> result = importHandler.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_android_randomized.json"), user, null, "timeline_from_android_randomized.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        // Verify that jobScheduler.enqueue was called since graceTimeSeconds is 0
        verify(jobScheduler, times(1)).enqueue(any(UUID.class), any(JobLambda.class), any(JobSchedulingService.Metadata.class));
    }
}