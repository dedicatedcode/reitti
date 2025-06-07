package com.dedicatedcode.reitti.service.processing;

public record GeoPoint(double latitude, double longitude) {

    @Override
    public String toString() {
        return "lat=" + latitude + ", lon=" + longitude + " -> (https://www.openstreetmap.org/#map=19/" + latitude + "/" + longitude + ")";
    }
}
