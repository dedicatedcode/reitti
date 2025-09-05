package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface GeocodeServiceManager {
    @Transactional
    Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace);
}
