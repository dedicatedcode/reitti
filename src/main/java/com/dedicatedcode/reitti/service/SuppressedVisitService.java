package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuppressedVisitService {

    private static final Logger logger = LoggerFactory.getLogger(SuppressedVisitService.class);

    private final SuppressedVisitJdbcService suppressedVisitJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final ManualRecalculationService manualRecalculationService;

    public SuppressedVisitService(SuppressedVisitJdbcService suppressedVisitJdbcService,
                                  RawLocationPointJdbcService rawLocationPointJdbcService,
                                  ManualRecalculationService manualRecalculationService) {
        this.suppressedVisitJdbcService = suppressedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.manualRecalculationService = manualRecalculationService;
    }

    @Transactional
    public void suppressVisit(User user, ProcessedVisit visit) {
        logger.info("Suppressing visit [{}] for user [{}] between [{}] and [{}]", visit.getId(), user.getUsername(), visit.getStartTime(), visit.getEndTime());
        SuppressedVisit suppressedVisit = new SuppressedVisit(
                visit.getPlace().getId(),
                visit.getPlace().getLatitudeCentroid(),
                visit.getPlace().getLongitudeCentroid(),
                visit.getStartTime(),
                visit.getEndTime());
        suppressedVisitJdbcService.create(user, suppressedVisit);
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, visit.getStartTime(), visit.getEndTime());
        manualRecalculationService.schedule(user, "Recalculate visits after suppressing a visit");
    }

    @Transactional
    public void restore(User user, SuppressedVisit suppressedVisit) {
        logger.info("Restoring suppressed visit [{}] for user [{}] between [{}] and [{}]", suppressedVisit.id(), user.getUsername(), suppressedVisit.startTime(), suppressedVisit.endTime());
        suppressedVisitJdbcService.delete(user, suppressedVisit.id());
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, suppressedVisit.startTime(), suppressedVisit.endTime());
        manualRecalculationService.schedule(user, "Recalculate visits after restoring a visit");
    }
}
