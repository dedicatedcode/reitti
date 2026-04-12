package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NominatimResultHandler implements ResultHandler{
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.NOMINATIM;
    }

    @Override
    public List<GeocodeResult> handle(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) return Collections.emptyList();

        List<JsonNode> resultList = new ArrayList<>();
        root.forEach(resultList::add);

        List<JsonNode> nodes = resultList.stream()
                .sorted(Comparator.comparingDouble((JsonNode n) -> n.path("importance").asDouble()))
                .toList();


        return nodes.stream().map(best -> {
                    JsonNode addr = best.path("address");
                    String label = best.path("display_name").asText();
                    String street = addr.path("road").asText("");
                    String city = addr.path("city").asText(addr.path("town").asText(addr.path("village").asText("")));
                    String district = addr.path("suburb").asText(addr.path("neighbourhood").asText(""));
                    String countryCode = addr.path("country_code").asText();
                    return createGeoCodeResult(label,
                                               street.trim(),
                                               addr.path("house_number").asText("").trim(),
                                               addr.path("post_code").asText("").trim(),
                                               city,
                                               district,
                                               countryCode,
                                               best.path("type").asText(),
                                               null);

                })
                .filter(Objects::nonNull)
                .toList();
    }
}
