package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;

public record PlaceInfo(Long id, String name, String address, SignificantPlace.PlaceType type, Double latitude, Double longitude) {
}
