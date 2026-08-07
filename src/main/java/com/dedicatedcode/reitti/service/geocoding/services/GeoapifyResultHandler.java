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
                .map(props -> {
                    String name = props.path("name").asText("");
                    String addressLine1 = props.path("address_line1").asText("");
                    String formatted = props.path("formatted").asText("");
                    String label = !name.isEmpty() ? name : (!addressLine1.isEmpty() ? addressLine1 : formatted);
                    return createGeoCodeResult(
                            label,
                            props.path("street").asText(""),
                            props.path("housenumber").asText(""),
                            props.path("postcode").asText(""),
                            props.path("city").asText(),
                            props.path("district").asText(),
                            props.path("country_code").asText(),
                            props.path("category").asText(), null
                    );
                })
                .filter(Objects::nonNull)
                .toList();

    }
}
