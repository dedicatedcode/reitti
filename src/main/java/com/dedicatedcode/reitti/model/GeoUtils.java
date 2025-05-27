package com.dedicatedcode.reitti.model;

public final class GeoUtils {
    private GeoUtils() {
    }

    public static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Earth radius in meters
        final double R = 6371000;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    public static double calculateHaversineDistance(RawLocationPoint p1, RawLocationPoint p2) {
        return calculateHaversineDistance(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
    }
}
