package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoapifyResultHandlerTest {

    private final GeoapifyResultHandler handler = new GeoapifyResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.GEO_APIFY));
    }

    @Test
    void testHandleSuccess() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "formatted": "Big Ben, London, UK",
                        "street": "Bridge Street",
                        "housenumber": "SW1A",
                        "city": "London",
                        "country_code": "gb",
                        "category": "tourism",
                        "rank": { "confidence": 1.0 }
                      }
                    }
                  ]
                }
                """;

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertTrue(result.isPresent());
        assertEquals("Big Ben, London, UK", result.get().label());
        assertEquals("Bridge Street", result.get().street());
        assertEquals("London", result.get().city());
        assertEquals("gb", result.get().countryCode());
    }
}
