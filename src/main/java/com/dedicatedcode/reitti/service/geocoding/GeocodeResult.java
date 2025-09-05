package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;

public record GeocodeResult(String label, String street, String houseNumber, String city, String postcode, String district, String countryCode, SignificantPlace.PlaceType placeType) {
}
