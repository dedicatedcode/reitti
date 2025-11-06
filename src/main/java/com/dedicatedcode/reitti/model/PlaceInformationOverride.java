package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;

import java.time.ZoneId;

public record PlaceInformationOverride(String name, SignificantPlace.PlaceType category, ZoneId timezone) {
}
