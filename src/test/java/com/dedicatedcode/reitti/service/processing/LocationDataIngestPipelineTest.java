package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class LocationDataIngestPipelineTest {

    @Autowired
    private RawLocationPointJdbcService repository;
    @Autowired
    private TestingService helper;
    @Autowired
    private TestingService testingService;

    @BeforeEach
    void setUp() {
        this.testingService.clearData();
    }

    @Test
    @Transactional
    void shouldStoreLocationDataIntoRepository() {
        helper.importData("/data/gpx/20250601.gpx");
        testingService.awaitDataImport(600);
        assertEquals(2463, this.repository.count());
    }
}