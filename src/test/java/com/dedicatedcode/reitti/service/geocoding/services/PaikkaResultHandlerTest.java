package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaikkaResultHandlerTest {

    private final PaikkaResultHandler handler = new PaikkaResultHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle(GeocoderType.PAIKKA));
    }

    @Test
    void testHandleSuccess() throws Exception {
        String json = """
                {
                  "results": [
                    {
                      "display_name": "Test Location",
                      "type": "building",
                      "distance_km": 0.1,
                      "address": {
                        "street": "Main Street",
                        "house_number": "10",
                        "postcode": "00100",
                        "city": "Helsinki"
                      },
                      "hierarchy": [
                        {"level": 10, "name": "Keskusta"},
                        {"level": 2, "country_code": "FI"}
                      ]
                    }
                  ]
                }
                """;

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertFalse(result.isEmpty());
        assertEquals("Test Location", result.getFirst().label());
        assertEquals("Main Street", result.getFirst().street());
        assertEquals("Helsinki", result.getFirst().city());
        assertEquals("fi", result.getFirst().countryCode());
    }
    @Test
    void testHandleShopFirst() throws Exception {
        InputStream is = getClass().getResourceAsStream("/data/geocoding/paikka/shop_first.json");
        assertNotNull(is, "Test resource not found");

        List<GeocodeResult> result = handler.handle(mapper.readTree(is));

        assertFalse(result.isEmpty(), "Expected a result");
        String label = result.getFirst().label();
        assertEquals("Sofia Spa", label, "Expected a shop, but got: " + label);
        assertEquals(SignificantPlace.PlaceType.OTHER, result.getFirst().placeType());
    }
}
