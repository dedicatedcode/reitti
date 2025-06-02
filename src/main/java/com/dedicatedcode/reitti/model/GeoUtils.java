package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.service.processing.StayPoint;

public final class GeoUtils {
    private GeoUtils() {
    }

    // Earth radius in meters
    private static final double EARTH_RADIUS = 6371000;

    public static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    public static double distanceInMeters(RawLocationPoint p1, RawLocationPoint p2) {
        return distanceInMeters(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
    }

    public static double distanceInMeters(StayPoint p1, StayPoint p2) {
        return distanceInMeters(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
    }

    /**
     * Converts a distance in meters to degrees of latitude and longitude at a given position.
     * The conversion varies based on the latitude because longitude degrees get closer together as you move away from the equator.
     * 
     * @param meters The distance in meters to convert
     * @param latitude The latitude at which to calculate the conversion
     * @return An array where index 0 is the latitude degrees and index 1 is the longitude degrees
     */
    public static double[] metersToDegreesAtPosition(double meters, double latitude) {
        // For latitude: 1 degree = 111,320 meters (approximately constant)
        double latitudeDegrees = meters / 111320.0;
        
        // For longitude: 1 degree = 111,320 * cos(latitude) meters (varies with latitude)
        double longitudeDegrees = meters / (111320.0 * Math.cos(Math.toRadians(latitude)));
        
        return new double[] { latitudeDegrees, longitudeDegrees };
    }
}
