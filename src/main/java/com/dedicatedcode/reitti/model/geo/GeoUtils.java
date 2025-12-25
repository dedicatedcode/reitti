package com.dedicatedcode.reitti.model.geo;

import com.dedicatedcode.reitti.dto.LocationPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeoUtils {
    private GeoUtils() {
    }

    // Earth radius in meters
    private static final double EARTH_RADIUS = 6371000;

    public static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        // For very small distances (< 1km), use faster approximation
        double latDiff = Math.abs(lat2 - lat1);
        double lonDiff = Math.abs(lon2 - lon1);
        
        if (latDiff < 0.01 && lonDiff < 0.01) { // roughly < 1km
            double avgLat = Math.toRadians((lat1 + lat2) / 2);
            double latDistance = Math.toRadians(latDiff);
            double lonDistance = Math.toRadians(lonDiff) * Math.cos(avgLat);

            return EARTH_RADIUS * Math.sqrt(latDistance * latDistance + lonDistance * lonDistance);
        }
        
        // Use precise haversine formula for longer distances
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        // Optimized: avoid atan2 when possible, use asin for better performance
        double c = 2 * Math.asin(Math.sqrt(a));

        return EARTH_RADIUS * c;
    }

    public static double distanceInMeters(GeoPoint p1, GeoPoint p2) {
        return distanceInMeters(
                p1.latitude(), p1.longitude(),
                p2.latitude(), p2.longitude());
    }

    public static double distanceInMeters(LocationPoint p1, LocationPoint p2) {
        return distanceInMeters(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
    }

    public static double distanceInMeters(RawLocationPoint p1, RawLocationPoint p2) {
        return distanceInMeters(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
    }

    /**
     * Converts a distance in meters to degrees of latitude and longitude at a given position.
     * The conversion varies based on the latitude because longitude degrees get closer together as you move away from the equator.
     *
     * @param meters   The distance in meters to convert
     * @param latitude The latitude at which to calculate the conversion
     * @return An array where index 0 is the latitude degrees and index 1 is the longitude degrees
     */
    public static double metersToDegreesAtPosition(double meters, double latitude) {
        // For longitude: 1 degree = 111,320 * cos(latitude) meters (varies with latitude)
        return meters / (111320.0 * Math.cos(Math.toRadians(latitude)));
    }

    public static double calculateTripDistance(List<RawLocationPoint> points) {
        if (points.size() < 2) {
            return 0.0;
        }
        List<RawLocationPoint> tmp = new ArrayList<>(points);
        tmp.sort(Comparator.comparing(RawLocationPoint::getTimestamp));

        double totalDistance = 0.0;

        for (int i = 0; i < tmp.size() - 1; i++) {
            RawLocationPoint p1 = tmp.get(i);
            RawLocationPoint p2 = tmp.get(i + 1);

            totalDistance += distanceInMeters(p1, p2);
        }

        return totalDistance;
    }

    public static GeoPoint calculatePolygonCentroid(List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon cannot be null or empty");
        }

        // Remove duplicate points (especially the closing point that duplicates the first point)
        List<GeoPoint> uniquePoints = removeDuplicates(polygon);

        // Calculate centroid as the arithmetic mean of unique vertices
        double avgLat = uniquePoints.stream().mapToDouble(GeoPoint::latitude).average().orElse(0.0);
        double avgLng = uniquePoints.stream().mapToDouble(GeoPoint::longitude).average().orElse(0.0);

        return new GeoPoint(avgLat, avgLng);
    }

    /**
     * Calculates the area of a polygon defined by a list of {@link GeoPoint}s.
     * <p>
     * The calculation uses a planar approximation (Shoelace formula) after converting
     * latitude/longitude to meters using an equirectangular projection. This is sufficient
     * for relatively small polygons (city‑scale or smaller) where the curvature of the
     * Earth can be ignored.
     *
     * @param polygon list of points defining the polygon. The points may be closed
     *                (first point repeated as last) – duplicates are ignored.
     * @return area in square meters
     * @throws IllegalArgumentException if the polygon is {@code null} or has fewer than
     *                                  three distinct points
     */
    public static double calculatePolygonArea(List<GeoPoint> polygon) {
        if (polygon == null) {
            throw new IllegalArgumentException("Polygon cannot be null");
        }

        // Remove duplicate points (including possible closing point)

        List<GeoPoint> uniquePoints = removeDuplicates(polygon);

        if (uniquePoints.size() < 3) {
            throw new IllegalArgumentException("Polygon must contain at least three distinct points");
        }

        // Convert each point to a Cartesian coordinate system in meters.
        // Latitude: approx 111,320 meters per degree.
        // Longitude: 111,320 * cos(latitude) meters per degree.
        double[] xs = new double[uniquePoints.size()];
        double[] ys = new double[uniquePoints.size()];

        for (int i = 0; i < uniquePoints.size(); i++) {
            GeoPoint p = uniquePoints.get(i);
            double latMeters = p.latitude() * 111320.0;
            double lonMeters = p.longitude() * 111320.0 * Math.cos(Math.toRadians(p.latitude()));
            xs[i] = lonMeters;
            ys[i] = latMeters;
        }

        // Shoelace formula
        double sum = 0.0;
        int n = uniquePoints.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += xs[i] * ys[j] - xs[j] * ys[i];
        }

        return Math.abs(sum) / 2.0;
    }

    private static List<GeoPoint> removeDuplicates(List<GeoPoint> polygon) {
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
        return uniquePoints;
    }

}
