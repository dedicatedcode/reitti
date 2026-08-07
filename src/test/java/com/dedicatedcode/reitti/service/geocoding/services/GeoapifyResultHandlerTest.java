package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoapifyResultHandlerTest {

    private final GeoapifyResultHandler handler = new GeoapifyResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.GEO_APIFY));
    }

    @Test
    void testHandleUsesNameAsLabel() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "name": "Big Ben",
                        "address_line1": "Big Ben",
                        "formatted": "Big Ben, Bridge Street, London, UK",
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

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Big Ben", result.getFirst().label());
    }

    @Test
    void testHandleUsesAddressLine1WhenNameMissing() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "address_line1": "Bridge Street 1",
                        "formatted": "Bridge Street 1, London, UK",
                        "street": "Bridge Street",
                        "housenumber": "1",
                        "postcode": "SW1A",
                        "city": "London",
                        "country_code": "gb",
                        "category": "building",
                        "rank": { "confidence": 1.0 }
                      }
                    }
                  ]
                }
                """;

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Bridge Street 1", result.getFirst().label());
    }

    @Test
    void testHandleFallsBackToFormatted() throws Exception {
        String json = """
                {
                  "features": [
                    {
                      "properties": {
                        "formatted": "London, UK",
                        "street": "",
                        "postcode": "",
                        "city": "London",
                        "country_code": "gb",
                        "category": "administrative",
                        "rank": { "confidence": 1.0 }
                      }
                    }
                  ]
                }
                """;

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("London, UK", result.getFirst().label());
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

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Big Ben, London, UK", result.getFirst().label());
        assertEquals("Bridge Street", result.getFirst().street());
        assertEquals("London", result.getFirst().city());
        assertEquals("gb", result.getFirst().countryCode());
    }
}
