package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
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
    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    @Transactional
    void shouldStoreLocationDataIntoRepository() {
        helper.importData(user, "/data/gpx/20250601.gpx");
        testingService.awaitDataImport(20);
        assertEquals(6381, this.repository.count());
    }
}