package com.dedicatedcode.reitti.service.geocoding;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface GeocodeServiceManager {
    @Transactional
    Optional<GeocodeResult> reverseGeocode(double latitude, double longitude);
}
