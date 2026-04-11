package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GeoapifyResultHandler implements ResultHandler{
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.GEO_APIFY;
    }

    @Override
    public List<GeocodeResult> handle(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonNode> featureList = new ArrayList<>();
        features.forEach(featureList::add);

        List<JsonNode> nodes = featureList.stream()
                .sorted(Comparator.comparingDouble((JsonNode n) -> n.path("properties").path("rank").path("confidence").asDouble()))
                .toList();

        return nodes.stream()
                .map(best -> best.path("properties"))
                .map(props -> createGeoCodeResult(
                        props.path("formatted").asText(),
                        props.path("street").asText(""),
                        props.path("housenumber").asText(""),
                        props.path("postcode").asText(""),
                        props.path("city").asText(),
                        props.path("district").asText(),
                        props.path("country_code").asText(),
                        props.path("category").asText(), null
                ))
                .filter(Objects::nonNull)
                .toList();

    }
}
