package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface GeocodeServiceManager {
    @Transactional
    Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse);

    GeocodeResult test(GeocoderType type, String url, String apiKey, String lang, double lat, double lng);
}
