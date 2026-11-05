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
        assertTrue(handler.canHandle(GeocoderType.NOMINATIM));
    }

    @Test
    void testHandleNominatimReverseResponse() throws Exception {
        // trimmed response of https://nominatim.openstreetmap.org/reverse?format=geocodejson&lat=52.5163&lon=13.3777
        String json = """
                {
                  "type": "FeatureCollection",
                  "geocoding": {"version": "0.1.0", "licence": "ODbL", "query": ""},
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "geocoding": {
                          "place_id": 144765639,
                          "osm_type": "way",
                          "osm_id": 518071791,
                          "osm_key": "building",
                          "osm_value": "gate",
                          "type": "house",
                          "label": "Brandenburger Tor, 1, Pariser Platz, Friedrich-Wilhelm-Stadt, Mitte, Berlin, 10117, Deutschland",
                          "name": "Brandenburger Tor",
                          "housenumber": "1",
                          "postcode": "10117",
                          "street": "Pariser Platz",
                          "district": "Mitte",
                          "city": "Berlin",
                          "country": "Deutschland",
                          "country_code": "de"
                        }
                      },
                      "geometry": {"type": "Point", "coordinates": [13.3777034, 52.5162699]}
                    }
                  ]
                }
                """;

        List<GeocodeResult> result = handler.handle(mapper.readTree(json));

        assertEquals(1, result.size());
        GeocodeResult first = result.getFirst();
        assertEquals("Brandenburger Tor, 1, Pariser Platz, Friedrich-Wilhelm-Stadt, Mitte, Berlin, 10117, Deutschland", first.label());
        assertEquals("Pariser Platz", first.street());
        assertEquals("1", first.houseNumber());
        assertEquals("10117", first.postcode());
        assertEquals("Berlin", first.city());
        assertEquals("Mitte", first.district());
        assertEquals("de", first.countryCode());
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
