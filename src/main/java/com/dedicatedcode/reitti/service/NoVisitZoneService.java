package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    @Transactional
    public NoVisitZone create(User user, NoVisitZone zone) {
        logger.info("Creating no-visit zone [{}] for user [{}]", zone.name(), user.getUsername());
        NoVisitZone created = noVisitZoneJdbcService.create(user, zone);
        manualRecalculationService.scheduleZoneArea(user, created.polygon(), "Recalculate visits after creating a no-visit zone");
        return created;
    }

    @Transactional
    public NoVisitZone updateGeometry(User user, NoVisitZone zone, List<GeoPoint> polygon) {
        logger.info("Updating no-visit zone [{}] for user [{}]", zone.id(), user.getUsername());
        NoVisitZone updated = noVisitZoneJdbcService.update(user, zone.withPolygon(polygon));
        manualRecalculationService.scheduleZoneArea(user, union(zone.polygon(), polygon), "Recalculate visits after editing a no-visit zone");
        return updated;
    }

    @Transactional
    public void delete(User user, NoVisitZone zone) {
        logger.info("Deleting no-visit zone [{}] for user [{}]", zone.id(), user.getUsername());
        noVisitZoneJdbcService.delete(user, zone.id());
        manualRecalculationService.scheduleZoneArea(user, zone.polygon(), "Recalculate visits after deleting a no-visit zone");
    }

    private List<GeoPoint> union(List<GeoPoint> first, List<GeoPoint> second) {
        List<GeoPoint> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}
