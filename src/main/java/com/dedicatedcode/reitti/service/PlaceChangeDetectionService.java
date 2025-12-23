package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlaceChangeDetectionService {

    private final SignificantPlaceJdbcService placeJdbcService;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    public PlaceChangeDetectionService(SignificantPlaceJdbcService placeJdbcService,
                                     I18nService i18nService,
                                     ObjectMapper objectMapper) {
        this.placeJdbcService = placeJdbcService;
        this.i18nService = i18nService;
        this.objectMapper = objectMapper;
    }

    public PlaceChangeAnalysis analyzeChanges(User user, Long placeId, String type, String polygonData) {
        try {
            SignificantPlace currentPlace = placeJdbcService.findById(placeId).orElseThrow();
            List<String> warnings = new ArrayList<>();

            // Analyze polygon changes
            analyzePolygonChanges(currentPlace, polygonData, warnings);

            // Analyze type changes
            analyzeTypeChanges(currentPlace, type, warnings);

            // Check for overlapping places
            analyzeOverlappingPlaces(user, placeId, polygonData, warnings);

            return new PlaceChangeAnalysis(warnings.isEmpty(), warnings);

        } catch (Exception e) {
            return new PlaceChangeAnalysis(false, List.of(i18nService.translate("places.warning.general_error", e.getMessage())));
        }
    }

    private void analyzePolygonChanges(SignificantPlace currentPlace, String polygonData, List<String> warnings) {
        boolean hadPolygon = currentPlace.getPolygon() != null && !currentPlace.getPolygon().isEmpty();
        boolean willHavePolygon = polygonData != null && !polygonData.trim().isEmpty();

        if (hadPolygon && !willHavePolygon) {
            warnings.add(i18nService.translate("places.warning.polygon.removal"));
        }

        if (!hadPolygon && willHavePolygon) {
            warnings.add(i18nService.translate("places.warning.polygon.addition"));
        }

        // Check if polygon is being significantly changed
        if (hadPolygon && willHavePolygon) {
            try {
                List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                GeoPoint newCentroid = calculatePolygonCentroid(newPolygon);
                GeoPoint currentCentroid = new GeoPoint(currentPlace.getLatitudeCentroid(), currentPlace.getLongitudeCentroid());

                // Calculate distance between centroids (rough approximation)
                double latDiff = Math.abs(newCentroid.latitude() - currentCentroid.latitude());
                double lngDiff = Math.abs(newCentroid.longitude() - currentCentroid.longitude());

                // If centroid moved significantly (more than ~10m at typical latitudes)
                if (latDiff > 0.0001 || lngDiff > 0.0001) {
                    warnings.add(i18nService.translate("places.warning.polygon.significant_change"));
                }
            } catch (Exception e) {
                // If polygon parsing fails, we'll catch it in the actual update
            }
        }
    }

    private void analyzeTypeChanges(SignificantPlace currentPlace, String type, List<String> warnings) {
        if (type != null && !type.isEmpty()) {
            try {
                SignificantPlace.PlaceType newType = SignificantPlace.PlaceType.valueOf(type);
                if (currentPlace.getType() != newType) {
                    warnings.add(i18nService.translate("places.warning.type.change",
                        i18nService.translate(currentPlace.getType().getMessageKey()),
                        i18nService.translate(newType.getMessageKey())));
                }
            } catch (IllegalArgumentException e) {
                // Invalid type will be handled in actual update
            }
        }
    }

    private void analyzeOverlappingPlaces(User user, Long placeId, String polygonData, List<String> warnings) {
        boolean willHavePolygon = polygonData != null && !polygonData.trim().isEmpty();
        
        if (willHavePolygon) {
            try {
                List<GeoPoint> newPolygon = parsePolygonData(polygonData);
                int overlappingPlaces = checkForOverlappingPlaces(user, placeId, newPolygon);
                if (overlappingPlaces > 0) {
                    warnings.add(i18nService.translate("places.warning.overlapping.visits", overlappingPlaces));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse polygon data: " + e.getMessage(), e);
            }
        }
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

    private GeoPoint calculatePolygonCentroid(List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon cannot be null or empty");
        }

        // Remove duplicate points (especially the closing point that duplicates the first point)
        List<GeoPoint> uniquePoints = new ArrayList<>();
        for (GeoPoint point : polygon) {
            boolean isDuplicate = uniquePoints.stream().anyMatch(existing ->
                Math.abs(existing.latitude() - point.latitude()) < 0.000001 &&
                Math.abs(existing.longitude() - point.longitude()) < 0.000001
            );
            if (!isDuplicate) {
                uniquePoints.add(point);
            }
        }

        // Calculate centroid as the arithmetic mean of unique vertices
        double avgLat = uniquePoints.stream().mapToDouble(GeoPoint::latitude).average().orElse(0.0);
        double avgLng = uniquePoints.stream().mapToDouble(GeoPoint::longitude).average().orElse(0.0);

        return new GeoPoint(avgLat, avgLng);
    }

    private int checkForOverlappingPlaces(User user, Long placeId, List<GeoPoint> newPolygon) {
        try {
            List<SignificantPlace> overlappingPlaces = placeJdbcService.findPlacesOverlappingWithPolygon(
                user.getId(), placeId, newPolygon);
            return overlappingPlaces.size();
        } catch (Exception e) {
            System.err.println("Error checking for overlapping places: " + e.getMessage());
            return 0;
        }
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
