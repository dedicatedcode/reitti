package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;

import java.util.List;

public record PlaceInfo(Long id, String name, String address, String city, String countryCode, Double lat, Double lng, SignificantPlace.PlaceType type, List<GeoPoint> polygon) {
}
