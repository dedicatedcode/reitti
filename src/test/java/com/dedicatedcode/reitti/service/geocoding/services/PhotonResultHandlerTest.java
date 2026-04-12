package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Eiffel Tower", result.getFirst().label());
        assertEquals("Avenue Anatole France", result.getFirst().street());
        assertEquals("5", result.getFirst().houseNumber());
        assertEquals("Paris", result.getFirst().city());
        assertEquals("fr", result.getFirst().countryCode());
    }
}
