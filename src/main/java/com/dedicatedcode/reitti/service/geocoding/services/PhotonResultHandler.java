package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.JsonNode;
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
        String street = props.path("street").asString("");
        String housenumber =  props.path("housenumber").asString("").trim();
        String postcode =  props.path("postcode").asString("").trim();

        return createGeoCodeResult(
                props.path("name").asString(""),
                street,
                housenumber,
                postcode,
                props.path("city").asString(),
                props.path("district").asString(),
                props.path("countrycode").asString(),
                props.path("osm_value").asString(),
                ""
        );

    }
}
