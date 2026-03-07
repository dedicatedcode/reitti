package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotonResultHandlerTest {

    private final PhotonResultHandler handler = new PhotonResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.PHOTON));
    }

    @Test
    void testHandleSuccess() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "name": "Eiffel Tower",
                        "street": "Avenue Anatole France",
                        "housenumber": "5",
                        "city": "Paris",
                        "countrycode": "FR",
                        "osm_value": "tourism"
                      }
                    }
                  ]
                }
                """;

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertTrue(result.isPresent());
        assertEquals("Eiffel Tower", result.get().label());
        assertEquals("Avenue Anatole France 5", result.get().street());
        assertEquals("Paris", result.get().city());
        assertEquals("FR", result.get().countryCode());
    }
}
