package com.dedicatedcode.reitti.service.geocoding;

public record GeocodeResult(String label, String street, String houseNumber, String city, String postcode, String district) {
}
