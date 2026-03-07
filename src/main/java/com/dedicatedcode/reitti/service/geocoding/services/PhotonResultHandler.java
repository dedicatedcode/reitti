package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PhotonResultHandler implements ResultHandler {
    @Override
    public boolean canHandle(GeocoderType type) {
        return type == GeocoderType.PHOTON;
    }

    @Override
    public Optional<GeocodeResult> handle(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) return Optional.empty();

        JsonNode props = features.get(0).path("properties");
        String street = props.path("street").asText("");
        String housenumber =  props.path("housenumber").asText("").trim();
        String postcode =  props.path("postcode").asText("").trim();

        return createGeoCodeResult(
                props.path("name").asText(""),
                street,
                housenumber,
                postcode,
                props.path("city").asText(),
                props.path("district").asText(),
                props.path("countrycode").asText(),
                props.path("osm_value").asText(),
                ""
        );

    }
}
