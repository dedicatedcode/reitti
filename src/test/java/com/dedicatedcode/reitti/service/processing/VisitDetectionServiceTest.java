package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
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
    private VisitRepository visitRepository;

    @Autowired
    private ProcessedVisitRepository processedVisitRepository;

    @Value("${reitti.visit.merge-threshold-seconds}")
    private int visitMergeThresholdSeconds;

    @BeforeEach
    void setUp() {
        this.testingService.clearData();
    }

    @Test
    @Transactional
    void shouldDetectVisits() {
        this.testingService.importData("/data/gpx/20250531.gpx");
        this.testingService.awaitDataImport(600);
        this.testingService.triggerProcessingPipeline();

        this.testingService.awaitDataImport(600);

        List<Visit> persistedVisits = this.visitRepository.findAll(Sort.by(Sort.Direction.ASC, "startTime"));

        assertEquals(11, persistedVisits.size());

        List<ProcessedVisit> processedVisits = this.processedVisitRepository.findAll(Sort.by(Sort.Direction.ASC, "startTime"));

        assertEquals(10, processedVisits.size());

        for (int i = 0; i < processedVisits.size() - 1; i++) {
            ProcessedVisit visit = processedVisits.get(i);
            ProcessedVisit nextVisit = processedVisits.get(i + 1);

            long durationBetweenVisits = Duration.between(visit.getEndTime(), nextVisit.getStartTime()).toSeconds();
            assertTrue(durationBetweenVisits >= visitMergeThresholdSeconds || !visit.getPlace().equals(nextVisit.getPlace()),
                    "Duration between same place visit at index [" + i + "] should not be lower than [" + visitMergeThresholdSeconds + "]s but was [" + durationBetweenVisits + "]s");
        }

        System.out.println();
    }
}