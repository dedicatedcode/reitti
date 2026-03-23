package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PaikkaResultHandler implements ResultHandler{
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.PAIKKA;
    }

    @Override
    public Optional<GeocodeResult> handle(JsonNode root) {
        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) return Optional.empty();

        List<JsonNode> resultList = new ArrayList<>();
        resultsNode.forEach(resultList::add);

        JsonNode best = resultList.stream()
                .min(Comparator.comparing(this::hasValidName, Comparator.reverseOrder())
                             .thenComparing(this::hasAddress, Comparator.reverseOrder())  // New: prioritize items with addresses
                             .thenComparingInt((JsonNode n) -> getPaikkaTypePriority(n.path("type").asString()))
                             .thenComparingDouble(n -> n.path("distance_km").asDouble()))
                .orElse(null);

        if (best == null) {
            return Optional.empty();
        }

        String label = best.path("display_name").asString("");
        if (!StringUtils.hasText(label) || label.equals("null")) label = best.path("names").path("default").asString("");

        JsonNode addr = best.path("address");
        String street = addr.path("street").asString("");
        String houseNumber = addr.path("house_number").asString("");
        String postcode = addr.path("postcode").asString("");
        String city = addr.path("city").asString("");

        String district = "";
        String countryCode = "";
        for (JsonNode level : best.path("hierarchy")) {
            if (level.path("level").asInt() == 10) district = level.path("name").asString();
            if (level.path("level").asInt() == 2) countryCode = level.path("country_code").asString();
        }

        return createGeoCodeResult(
                label,
                street.trim(),
                houseNumber,
                postcode,
                city,
                district,
                countryCode,
                best.path("type").asString(),
                best.path("subtype").asString()
        );
    }

    private boolean hasValidName(JsonNode node) {
        String displayName = node.path("display_name").asString("");
        if (StringUtils.hasText(displayName) && !displayName.equals("null")) {
            return true;
        }
        String defaultName = node.path("names").path("default").asString("");
        return StringUtils.hasText(defaultName) && !defaultName.equals("null");
    }

    private boolean hasAddress(JsonNode node) {
        JsonNode addr = node.path("address");
        return !addr.isMissingNode() && !addr.isNull() &&
                (StringUtils.hasText(addr.path("street").asString()) ||
                        StringUtils.hasText(addr.path("city").asString()));
    }

    private int getPaikkaTypePriority(String type) {
        return switch (type) {
            case "amenity" -> 1;
            case "tourism" -> 2;
            case "place" -> 3;
            case "building" -> 4;
            default -> 10;
        };
    }
}
