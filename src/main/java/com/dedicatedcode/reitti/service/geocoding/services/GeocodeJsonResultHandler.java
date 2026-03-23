package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GeocodeJsonResultHandler implements ResultHandler {
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.GEOCODE_JSON;
    }

    @Override
    public Optional<GeocodeResult> handle(JsonNode root) {
        JsonNode feature = root.path("features").path(0);
        if (feature.isMissingNode()) return Optional.empty();

        JsonNode geocoding = feature.path("properties").path("geocoding");
        String label = geocoding.path("label").asString(geocoding.path("name").asString());
        String street = geocoding.path("street").asString("");

        return createGeoCodeResult(
                label,
                street.trim(),
                geocoding.path("housenumber").asString("").trim(),
                geocoding.path("postcode").asString(""),
                geocoding.path("city").asString(),
                geocoding.path("district").asString(),
                geocoding.path("country_code").asString(),
                geocoding.path("type").asString(),
                null
        );
    }
}
