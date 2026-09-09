package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.geo.Visit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class VisitSuppressionService {

    private static final Logger logger = LoggerFactory.getLogger(VisitSuppressionService.class);

    private final SuppressedVisitJdbcService suppressedVisitJdbcService;
    private final NoVisitZoneJdbcService noVisitZoneJdbcService;
    private final GeometryFactory geometryFactory;

    public VisitSuppressionService(SuppressedVisitJdbcService suppressedVisitJdbcService,
                                   NoVisitZoneJdbcService noVisitZoneJdbcService,
                                   GeometryFactory geometryFactory) {
        this.suppressedVisitJdbcService = suppressedVisitJdbcService;
        this.noVisitZoneJdbcService = noVisitZoneJdbcService;
        this.geometryFactory = geometryFactory;
    }

    public List<Visit> removeVisitsInsideNoVisitZones(User user, List<Visit> visits) {
        if (visits.isEmpty()) {
            return visits;
        }
        List<NoVisitZone> zones = noVisitZoneJdbcService.findByUser(user);
        if (zones.isEmpty()) {
            return visits;
        }

        List<Polygon> polygons = zones.stream()
                .map(zone -> toJtsPolygon(zone.polygon()))
                .toList();

        List<Visit> kept = new ArrayList<>(visits.size());
        for (Visit visit : visits) {
            Point centroid = geometryFactory.createPoint(new Coordinate(visit.getLongitude(), visit.getLatitude()));
            boolean insideZone = polygons.stream().anyMatch(polygon -> polygon.contains(centroid));
            if (insideZone) {
                logger.info("Suppressing visit at [{}] between [{}] and [{}] because it lies inside a no-visit zone",
                        centroid, visit.getStartTime(), visit.getEndTime());
            } else {
                kept.add(visit);
            }
        }
        return kept;
    }

    public List<ProcessedVisit> removeSuppressedVisits(User user, Instant windowStart, Instant windowEnd, List<ProcessedVisit> processedVisits, double placeRadiusMeters) {
        if (processedVisits.isEmpty()) {
            return processedVisits;
        }
        List<SuppressedVisit> suppressedVisits = suppressedVisitJdbcService.findByUserAndTimeOverlap(user, windowStart, windowEnd);
        if (suppressedVisits.isEmpty()) {
            return processedVisits;
        }

        List<ProcessedVisit> kept = new ArrayList<>(processedVisits.size());
        for (ProcessedVisit visit : processedVisits) {
            boolean suppressed = suppressedVisits.stream().anyMatch(s -> matches(s, visit, placeRadiusMeters));
            if (suppressed) {
                logger.info("Suppressing visit at place [{}] between [{}] and [{}] because this time frame was suppressed by the user",
                        visit.getPlace().getId(), visit.getStartTime(), visit.getEndTime());
            } else {
                kept.add(visit);
            }
        }
        return kept;
    }

    private boolean matches(SuppressedVisit suppressedVisit, ProcessedVisit visit, double placeRadiusMeters) {
        boolean overlaps = visit.getStartTime().isBefore(suppressedVisit.endTime())
                && visit.getEndTime().isAfter(suppressedVisit.startTime());
        if (!overlaps) {
            return false;
        }
        if (suppressedVisit.placeId() != null && suppressedVisit.placeId().equals(visit.getPlace().getId())) {
            return true;
        }
        //Fallback for full recalculations where places were recreated with new ids
        return GeoUtils.distanceInMeters(
                suppressedVisit.latitudeCentroid(), suppressedVisit.longitudeCentroid(),
                visit.getPlace().getLatitudeCentroid(), visit.getPlace().getLongitudeCentroid()) <= placeRadiusMeters;
    }

    private Polygon toJtsPolygon(List<GeoPoint> polygon) {
        List<Coordinate> coordinates = new ArrayList<>(polygon.size() + 1);
        for (GeoPoint point : polygon) {
            coordinates.add(new Coordinate(point.longitude(), point.latitude()));
        }
        GeoPoint first = polygon.getFirst();
        GeoPoint last = polygon.getLast();
        if (first.latitude() != last.latitude() || first.longitude() != last.longitude()) {
            coordinates.add(new Coordinate(first.longitude(), first.latitude()));
        }
        LinearRing ring = geometryFactory.createLinearRing(coordinates.toArray(new Coordinate[0]));
        return geometryFactory.createPolygon(ring);
    }
}
