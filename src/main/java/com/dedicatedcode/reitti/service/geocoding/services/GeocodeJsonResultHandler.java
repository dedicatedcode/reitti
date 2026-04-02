package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
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
        String label = geocoding.path("label").asText(geocoding.path("name").asText());
        String street = geocoding.path("street").asText("");

        return createGeoCodeResult(
                label,
                street.trim(),
                geocoding.path("housenumber").asText("").trim(),
                geocoding.path("postcode").asText(""),
                geocoding.path("city").asText(),
                geocoding.path("district").asText(),
                geocoding.path("country_code").asText(),
                geocoding.path("type").asText(),
                null
        );
    }
}
