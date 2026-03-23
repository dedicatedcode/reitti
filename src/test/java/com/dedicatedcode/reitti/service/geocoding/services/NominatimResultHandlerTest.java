package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominatimResultHandlerTest {

    private final NominatimResultHandler handler = new NominatimResultHandler();
    private final ObjectMapper mapper = new JsonMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.NOMINATIM));
    }

    @Test
    void testHandleSuccess() throws Exception {
        String json = """
                [
                  {
                    "display_name": "Brandenburg Gate, Berlin, Germany",
                    "importance": 0.9,
                    "type": "monument",
                    "address": {
                      "road": "Pariser Platz",
                      "house_number": "1",
                      "city": "Berlin",
                      "post_code": "10117",
                      "country_code": "de"
                    }
                  }
                ]
                """;

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertTrue(result.isPresent());
        assertEquals("Brandenburg Gate, Berlin, Germany", result.get().label());
        assertEquals("Pariser Platz", result.get().street());
        assertEquals("Berlin", result.get().city());
        assertEquals("de", result.get().countryCode());
    }
}
