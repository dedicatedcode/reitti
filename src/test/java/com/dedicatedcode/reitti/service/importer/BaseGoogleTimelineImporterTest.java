package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.User;
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
        assertEquals(6, createdVisits.size());
        //"startTime" : "2017-05-02T12:12:04+10:00",
        //"endTime" : "2017-05-02T18:52:12+10:00",

        //"startTime" : "2017-05-02T19:16:01+10:00",
        //    "endTime" : "2017-05-02T20:48:52+10:00",

        // "startTime" : "2017-05-02T21:17:03+10:00",
        //    "endTime" : "2017-05-03T14:23:20+10:00",

        // "startTime" : "2017-05-03T15:10:14+10:00",
        //    "endTime" : "2017-05-03T23:50:01+10:00",

        // "startTime" : "2017-05-04T00:05:33+10:00",
        //    "endTime" : "2017-05-04T00:17:23+10:00",

        //"startTime" : "2017-05-04T00:08:21+10:00",
        //    "endTime" : "2017-05-04T00:17:23+10:00",

        //"startTime" : "2017-05-04T00:44:27+10:00",
        //    "endTime" : "2017-05-04T14:51:51+10:00",
    }
}
