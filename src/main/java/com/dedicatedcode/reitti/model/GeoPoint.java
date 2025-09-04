package com.dedicatedcode.reitti.model;

import org.locationtech.jts.geom.Point;

public record GeoPoint(double latitude, double longitude) {
    public static GeoPoint from(Point point) {
        return new  GeoPoint(point.getY(), point.getX());
    }

    public static GeoPoint from(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    public boolean near(GeoPoint point) {
        return GeoUtils.distanceInMeters(this, point) < 100;
    }

    @Override
    public String toString() {
        return "[lat,long]=[" + latitude + "," + longitude + "] -> (https://www.google.com/maps/search/?api=1&query="+latitude+","+longitude+")";
    }
}
