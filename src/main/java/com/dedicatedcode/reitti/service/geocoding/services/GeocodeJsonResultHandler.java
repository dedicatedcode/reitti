package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GeocodeJsonResultHandler implements ResultHandler {
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.GEOCODE_JSON;
    }

    @Override
    public List<GeocodeResult> handle(JsonNode root) {
        JsonNode features = root.path("features");
        if (features.isMissingNode() || features.isEmpty()) return Collections.emptyList();

        List<GeocodeResult> results = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode geocoding = feature.path("properties").path("geocoding");
            String label = geocoding.path("label").asText(geocoding.path("name").asText());
            String street = geocoding.path("street").asText("");

            GeocodeResult geoCodeResult = createGeoCodeResult(
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
            if (geoCodeResult != null) {
                results.add(geoCodeResult);
            }
        }
        return results;
    }
}
