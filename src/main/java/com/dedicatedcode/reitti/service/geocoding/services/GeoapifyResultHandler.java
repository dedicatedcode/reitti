package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.JsonNode;
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
                props.path("formatted").asString(),
                props.path("street").asString(""),
                props.path("housenumber").asString(""),
                props.path("postcode").asString(""),
                props.path("city").asString(),
                props.path("district").asString(),
                props.path("country_code").asString(),
                props.path("category").asString(), null
        );

    }
}
