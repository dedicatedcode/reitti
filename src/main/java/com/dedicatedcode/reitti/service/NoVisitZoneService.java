package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NoVisitZoneService {

    private static final Logger logger = LoggerFactory.getLogger(NoVisitZoneService.class);

    private final NoVisitZoneJdbcService noVisitZoneJdbcService;
    private final ManualRecalculationService manualRecalculationService;

    public NoVisitZoneService(NoVisitZoneJdbcService noVisitZoneJdbcService,
                              ManualRecalculationService manualRecalculationService) {
        this.noVisitZoneJdbcService = noVisitZoneJdbcService;
        this.manualRecalculationService = manualRecalculationService;
    }

    public NoVisitZone create(User user, NoVisitZone zone) {
        logger.info("Creating no-visit zone [{}] for user [{}]", zone.name(), user.getUsername());
        NoVisitZone created = noVisitZoneJdbcService.create(user, zone);
        manualRecalculationService.scheduleZoneArea(user, created.polygon(), "Recalculate visits after creating a no-visit zone");
        return created;
    }

    public void delete(User user, NoVisitZone zone) {
        logger.info("Deleting no-visit zone [{}] for user [{}]", zone.id(), user.getUsername());
        noVisitZoneJdbcService.delete(user, zone.id());
        manualRecalculationService.scheduleZoneArea(user, zone.polygon(), "Recalculate visits after deleting a no-visit zone");
    }
}
