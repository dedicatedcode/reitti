package com.dedicatedcode.reitti.model;

public record GeoPoint(double latitude, double longitude) {

    @Override
    public String toString() {
        return "lat=" + latitude + ", lon=" + longitude + " -> (https://www.openstreetmap.org/#map=19/" + latitude + "/" + longitude + ")";
    }
}
