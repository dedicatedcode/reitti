package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class GpxImporterTest {

    @Autowired
    private GpxImporter gpxImporter;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = this.testingService.randomUser();
    }

    @Test
    void shouldImportDefaultGPXFile() {
        InputStream stream = getClass().getResourceAsStream("/data/gpx/20250617.gpx");
        gpxImporter.importGpx(stream, user, null, null);
        this.testingService.awaitDataImport(30);
        assertFalse(processedVisitJdbcService.findByUser(user).isEmpty());
    }
}