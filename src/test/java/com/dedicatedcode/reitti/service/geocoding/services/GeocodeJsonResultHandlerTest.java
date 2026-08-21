package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Statue of Liberty", result.getFirst().label());
        assertEquals("Liberty Island Road", result.getFirst().street());
        assertEquals("New York", result.getFirst().city());
    }
}
