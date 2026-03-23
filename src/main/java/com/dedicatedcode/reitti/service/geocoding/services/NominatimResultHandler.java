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
public class NominatimResultHandler implements ResultHandler{
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.NOMINATIM;
    }

    @Override
    public Optional<GeocodeResult> handle(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) return Optional.empty();

        List<JsonNode> resultList = new ArrayList<>();
        root.forEach(resultList::add);

        JsonNode best = resultList.stream()
                .max(Comparator.comparingDouble((JsonNode n) -> n.path("importance").asDouble()))
                .orElse(root.get(0));

        JsonNode addr = best.path("address");
        String label = best.path("display_name").asString();
        String street = addr.path("road").asString("");
        String city = addr.path("city").asString(addr.path("town").asString(addr.path("village").asString("")));
        String district = addr.path("suburb").asString(addr.path("neighbourhood").asString(""));
        String countryCode = addr.path("country_code").asString();

        return createGeoCodeResult(label,
                                   street.trim(),
                                   addr.path("house_number").asString("").trim(),
                                   addr.path("post_code").asString("").trim(),
                                   city,
                                   district,
                                   countryCode,
                                   best.path("type").asString(),
                                   null);
    }
}
