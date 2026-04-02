package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GeoapifyResultHandler implements ResultHandler{
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.GEO_APIFY;
    }

    @Override
    public Optional<GeocodeResult> handle(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) return Optional.empty();

        List<JsonNode> featureList = new ArrayList<>();
        features.forEach(featureList::add);

        JsonNode best = featureList.stream()
                .max(Comparator.comparingDouble((JsonNode n) -> n.path("properties").path("rank").path("confidence").asDouble()))
                .orElse(features.get(0));

        JsonNode props = best.path("properties");
        return createGeoCodeResult(
                props.path("formatted").asText(),
                props.path("street").asText(""),
                props.path("housenumber").asText(""),
                props.path("postcode").asText(""),
                props.path("city").asText(),
                props.path("district").asText(),
                props.path("country_code").asText(),
                props.path("category").asText(), null
        );

    }
}
