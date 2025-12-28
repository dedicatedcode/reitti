package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;

import java.time.ZoneId;
import java.util.List;

public record PlaceInformationOverride(String name, SignificantPlace.PlaceType category, ZoneId timezone, List<GeoPoint> polygon) {
}
