package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Visit;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.VisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VisitDetectionServiceTest {

    @Autowired
    private TestingService testingService;

    @Autowired
    private VisitJdbcService visitRepository;

    @Autowired
    private ProcessedVisitJdbcService processedVisitRepository;
    @Autowired
    private UserJdbcService userJdbcService;

    @BeforeEach
    void setUp() {
        this.testingService.clearData();
    }

    @Test
    @Transactional
    void shouldDetectVisits() {
        this.testingService.importAndProcess("/data/gpx/20250531.gpx");

        List<Visit> persistedVisits = this.visitRepository.findByUser(userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L)));

        assertEquals(15, persistedVisits.size());

        List<ProcessedVisit> processedVisits = this.processedVisitRepository.findByUser(userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L)));

        assertEquals(9, processedVisits.size());

        for (int i = 0; i < processedVisits.size() - 1; i++) {
            ProcessedVisit visit = processedVisits.get(i);
            ProcessedVisit nextVisit = processedVisits.get(i + 1);

            long durationBetweenVisits = Duration.between(visit.getEndTime(), nextVisit.getStartTime()).toSeconds();
            assertTrue(durationBetweenVisits >= 300 || !visit.getPlace().equals(nextVisit.getPlace()),
                    "Duration between same place visit at index [" + i + "] should not be lower than [" + 300 + "]s but was [" + durationBetweenVisits + "]s");
        }

        System.out.println();
    }
}