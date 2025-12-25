package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DataCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final DefaultImportProcessor defaultImportProcessor;

    public DataCleanupService(TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              SignificantPlaceJdbcService significantPlaceJdbcService,
                              RawLocationPointJdbcService rawLocationPointJdbcService,
                              DefaultImportProcessor defaultImportProcessor) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.defaultImportProcessor = defaultImportProcessor;
    }

    public void cleanupForGeometryChange(User user, List<SignificantPlace> placesToRemove, List<LocalDate> affectedDays) {
        long start = System.nanoTime();
        log.info("Cleanup for geometry change. Removing [{}] places and starting recalculation for days [{}]", placesToRemove.size(), affectedDays);
        log.debug("Removing affected trips for places [{}]", placesToRemove);
        this.tripJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing affected visits for places [{}]", placesToRemove);
        this.processedVisitJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing places [{}]", placesToRemove);
        this.significantPlaceJdbcService.deleteForUser(user, placesToRemove);
        log.info("Cleanup for geometry change completed in {}ms", (System.nanoTime() - start) / 1000000);

        start = System.nanoTime();
        this.rawLocationPointJdbcService.markAllAsUnprocessedForUser(user, affectedDays);
        log.info("clearing processed points for days [{}] completed in {}ms", affectedDays, (System.nanoTime() - start) / 1000000);
        this.defaultImportProcessor.scheduleProcessingTrigger(user.getUsername());

    }
}
