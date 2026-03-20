package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
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
                             .thenComparingInt((JsonNode n) -> getPaikkaTypePriority(n.path("type").asText()))
                             .thenComparingDouble(n -> n.path("distance_km").asDouble()))
                .orElse(null);

        if (best == null) {
            return Optional.empty();
        }

        String label = best.path("display_name").asText("");
        if (!StringUtils.hasText(label) || label.equals("null")) label = best.path("names").path("default").asText("");

        JsonNode addr = best.path("address");
        String street = addr.path("street").asText("");
        String houseNumber = addr.path("house_number").asText("");
        String postcode = addr.path("postcode").asText("");
        String city = addr.path("city").asText("");

        String district = "";
        String countryCode = "";
        for (JsonNode level : best.path("hierarchy")) {
            if (level.path("level").asInt() == 10) district = level.path("name").asText();
            if (level.path("level").asInt() == 2) countryCode = level.path("country_code").asText();
        }

        return createGeoCodeResult(
                label,
                street.trim(),
                houseNumber,
                postcode,
                city,
                district,
                countryCode,
                best.path("type").asText(),
                best.path("subtype").asText()
        );
    }

    private boolean hasValidName(JsonNode node) {
        String displayName = node.path("display_name").asText("");
        if (StringUtils.hasText(displayName) && !displayName.equals("null")) {
            return true;
        }
        String defaultName = node.path("names").path("default").asText("");
        return StringUtils.hasText(defaultName) && !defaultName.equals("null");
    }

    private boolean hasAddress(JsonNode node) {
        JsonNode addr = node.path("address");
        return !addr.isMissingNode() && !addr.isNull() &&
                (StringUtils.hasText(addr.path("street").asText()) ||
                        StringUtils.hasText(addr.path("city").asText()));
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
