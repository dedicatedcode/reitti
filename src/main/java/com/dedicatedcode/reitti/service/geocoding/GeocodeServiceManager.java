package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GeocodeServiceManager {
    Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse);

    GeocodeResult test(GeocodeService service, double testLat, double testLng);

    Map<GeocoderType, List<GeocodeResult>> reverseGeocodeAll(SignificantPlace significantPlace);
}
