package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
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

    public DataCleanupService(TripJdbcService tripJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              SignificantPlaceJdbcService significantPlaceJdbcService) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
    }

    public void cleanupForGeometryChange(User user, List<SignificantPlace> placesToRemove, List<LocalDate> affectedDays) {
        log.info("Cleanup for geometry change. Removing [{}] places and starting recalculation for days [{}]", placesToRemove.size(), affectedDays);

        log.debug("Removing affected trips for places [{}]", placesToRemove);
        this.tripJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing affected visits for places [{}]", placesToRemove);
        this.processedVisitJdbcService.deleteFor(user, placesToRemove);
        log.debug("Removing places [{}]", placesToRemove);
        this.significantPlaceJdbcService.deleteForUser(user, placesToRemove);
    }
}
