package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class GoogleRecordsImporterTest {

    @Autowired
    private GoogleRecordsImporter googleRecordsImporter;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TestingService testingService;
    private User user;

    @BeforeEach
    void setUp() {
        this.user = this.testingService.randomUser();
    }

    @Test
    void shouldParseOldFormat() {
        Map<String, Object> result = googleRecordsImporter.importGoogleRecords(getClass().getResourceAsStream("/data/google/Records.json"), user, null, "Records.json");

        assertTrue(result.containsKey("success"));
        assertTrue((Boolean) result.get("success"));

        this.testingService.awaitDataImport(30);
        assertFalse(processedVisitJdbcService.findByUser(user).isEmpty());
    }
}