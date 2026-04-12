package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NominatimResultHandlerTest {

    private final NominatimResultHandler handler = new NominatimResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

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

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Brandenburg Gate, Berlin, Germany", result.getFirst().label());
        assertEquals("Pariser Platz", result.getFirst().street());
        assertEquals("Berlin", result.getFirst().city());
        assertEquals("de", result.getFirst().countryCode());
    }
}
