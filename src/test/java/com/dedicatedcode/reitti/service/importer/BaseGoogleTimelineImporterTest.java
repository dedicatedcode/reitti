package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class BaseGoogleTimelineImporterTest {

    @Autowired
    private TestingService testingService;

    @Autowired
    private GoogleAndroidTimelineImporter googleTimelineImporter;

    @Autowired
    private ProcessedVisitJdbcService visitJdbcService;

    @Autowired
    private ProcessingPipelineTrigger trigger;
    @Test
    void shouldParseNewGoogleTakeOutFileFromAndroid() {
        User user = testingService.admin();
        Map<String, Object> result = googleTimelineImporter.importTimeline(getClass().getResourceAsStream("/data/google/timeline_from_android_randomized.json"), user);

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        testingService.awaitDataImport(20);
        trigger.start();
        testingService.awaitDataImport(20);

        List<ProcessedVisit> createdVisits = this.visitJdbcService.findByUser(user);
        assertEquals(3, createdVisits.size());
    }
}
