package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PlaceChangeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PlaceChangeDetectionService.class);
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final SignificantPlaceJdbcService placeJdbcService;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    public PlaceChangeDetectionService(ProcessedVisitJdbcService processedVisitJdbcService,
                                       SignificantPlaceJdbcService placeJdbcService,
                                       I18nService i18nService,
                                       ObjectMapper objectMapper) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.placeJdbcService = placeJdbcService;
        this.i18nService = i18nService;
        this.objectMapper = objectMapper;
    }

    public PlaceChangeAnalysis analyzeChanges(User user, Long placeId, String polygonData) {
        try {
            SignificantPlace currentPlace = placeJdbcService.findById(placeId).orElseThrow();
            List<String> warnings = new ArrayList<>();

            // Analyze polygon changes
            boolean changed = analyzePolygonChanges(currentPlace, polygonData, warnings);

            // Check for overlapping places
            changed = changed | analyzeOverlappingPlaces(user, placeId, polygonData, warnings);

            //check for affected days only when places got merged or the polygon changed significantly
            if (changed) {
                calculateAffectedDays(user, currentPlace, polygonData, warnings);
            }

            return new PlaceChangeAnalysis(warnings.isEmpty(), warnings);

        } catch (Exception e) {
            return new PlaceChangeAnalysis(false, List.of(i18nService.translate("places.warning.general_error", e.getMessage())));
        }
    }

    private void calculateAffectedDays(User user, SignificantPlace currentPlace, String polygonData, List<String> warnings) throws Exception {
        List<SignificantPlace> overlappingPlaces;
        if (polygonData != null) {
            overlappingPlaces = new ArrayList<>(checkForOverlappingPlaces(user, currentPlace.getId(), Collections.emptyList()));
            overlappingPlaces.add(currentPlace);
        } else {
            overlappingPlaces = List.of(currentPlace);
        }
        int affectedDays = this.processedVisitJdbcService.getAffectedDays(overlappingPlaces).size();
        if (affectedDays > 0) {
            warnings.add(i18nService.translate("places.warning.overlapping.recalculation_hint", affectedDays));
        }
    }

    private boolean analyzePolygonChanges(SignificantPlace currentPlace, String polygonData, List<String> warnings) {
        boolean changed = false;
        boolean hadPolygon = currentPlace.getPolygon() != null && !currentPlace.getPolygon().isEmpty();
        boolean willHavePolygon = polygonData != null && !polygonData.trim().isEmpty();

        if (hadPolygon && !willHavePolygon) {
            warnings.add(i18nService.translate("places.warning.polygon.removal"));
            changed = true;
        }

        if (!hadPolygon && willHavePolygon) {
            warnings.add(i18nService.translate("places.warning.polygon.addition"));
            changed = true;
        }

        // Check if polygon is being significantly changed
        if (hadPolygon && willHavePolygon) {
            try {
                List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                GeoPoint newCentroid = GeoUtils.calculatePolygonCentroid(newPolygon);
                GeoPoint currentCentroid = new GeoPoint(currentPlace.getLatitudeCentroid(), currentPlace.getLongitudeCentroid());

                double currentArea = GeoUtils.calculatePolygonArea(currentPlace.getPolygon());
                double newArea = GeoUtils.calculatePolygonArea(newPolygon);
                // Calculate distance between centroids (rough approximation)
                double latDiff = Math.abs(newCentroid.latitude() - currentCentroid.latitude());
                double lngDiff = Math.abs(newCentroid.longitude() - currentCentroid.longitude());

                // If centroid moved significantly (more than ~10m at typical latitudes)
                if (latDiff > 0.0001 || lngDiff > 0.0001 || Math.abs(newArea - currentArea) > 1) {
                    warnings.add(i18nService.translate("places.warning.polygon.significant_change"));
                    changed = true;
                }
            } catch (Exception e) {
                // If polygon parsing fails, we'll catch it in the actual update
            }
        }
        return changed;
    }

    private boolean analyzeOverlappingPlaces(User user, Long placeId, String polygonData, List<String> warnings) {
        boolean willHavePolygon = polygonData != null && !polygonData.trim().isEmpty();
        
        if (willHavePolygon) {
            try {
                List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                int overlappingPlaces = checkForOverlappingPlaces(user, placeId, newPolygon).size();
                if (overlappingPlaces > 0) {
                    warnings.add(i18nService.translate("places.warning.overlapping.visits", overlappingPlaces));
                    return true;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse polygon data: " + e.getMessage(), e);
            }
        }
        return false;
    }

    private List<GeoPoint> parsePolygonData(String polygonData) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(polygonData);
        List<GeoPoint> geoPoints = new ArrayList<>();

        if (jsonNode.isArray()) {
            for (JsonNode pointNode : jsonNode) {
                if (pointNode.has("lat") && pointNode.has("lng")) {
                    double lat = pointNode.get("lat").asDouble();
                    double lng = pointNode.get("lng").asDouble();
                    geoPoints.add(new GeoPoint(lat, lng));
                } else {
                    throw new IllegalArgumentException("Each point must have 'lat' and 'lng' properties");
                }
            }
        } else {
            throw new IllegalArgumentException("Polygon data must be an array of coordinate objects");
        }

        if (geoPoints.size() < 3) {
            throw new IllegalArgumentException("Polygon must have at least 3 points");
        }

        return geoPoints;
    }

    private List<SignificantPlace> checkForOverlappingPlaces(User user, Long placeId, List<GeoPoint> newPolygon) {
        return placeJdbcService.findPlacesOverlappingWithPolygon(user.getId(), placeId, newPolygon);
    }

    public static class PlaceChangeAnalysis {
        private final boolean canProceed;
        private final List<String> warnings;

        public PlaceChangeAnalysis(boolean canProceed, List<String> warnings) {
            this.canProceed = canProceed;
            this.warnings = warnings;
        }

        public boolean isCanProceed() {
            return canProceed;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
