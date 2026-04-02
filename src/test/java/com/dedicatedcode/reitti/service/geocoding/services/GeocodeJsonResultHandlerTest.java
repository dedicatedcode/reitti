package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeocodeJsonResultHandlerTest {

    private final GeocodeJsonResultHandler handler = new GeocodeJsonResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.GEOCODE_JSON));
    }

    @Test
    void testHandleSuccess() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "geocoding": {
                          "label": "Statue of Liberty",
                          "name": "Liberty Island",
                          "street": "Liberty Island Road",
                          "city": "New York",
                          "type": "monument"
                        }
                      }
                    }
                  ]
                }
                """;

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertTrue(result.isPresent());
        assertEquals("Statue of Liberty", result.get().label());
        assertEquals("Liberty Island Road", result.get().street());
        assertEquals("New York", result.get().city());
    }
}
