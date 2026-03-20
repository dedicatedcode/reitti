package com.dedicatedcode.reitti.service.geocoding.services;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

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

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertTrue(result.isPresent());
        assertEquals("Test Location", result.get().label());
        assertEquals("Main Street", result.get().street());
        assertEquals("Helsinki", result.get().city());
        assertEquals("fi", result.get().countryCode());
    }
    @Test
    void testHandleShopFirst() throws Exception {
        InputStream is = getClass().getResourceAsStream("/data/geocoding/paikka/shop_first.json");
        assertNotNull(is, "Test resource not found");

        Optional<GeocodeResult> result = handler.handle(mapper.readTree(is));

        assertTrue(result.isPresent(), "Expected a result");
        String label = result.get().label();
        assertEquals("Sofia Spa", label, "Expected a shop, but got: " + label);
        assertEquals(SignificantPlace.PlaceType.OTHER, result.get().placeType());
    }
}
