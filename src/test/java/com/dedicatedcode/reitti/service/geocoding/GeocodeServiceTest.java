package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeocodeServiceTest {

    @Test
    void testGetUrlTemplatePhoton() {
        GeocodeService service = new GeocodeService("Photon", "https://photon.example.com", true, 0, null, null, GeocoderType.PHOTON, 1, Map.of());
        String template = service.getUrlTemplate();
        assertEquals("https://photon.example.com/reverse?lon={lng}&lat={lat}&limit=10&layer=house&layer=locality&radius=0.03", template);
    }

    @Test
    void testGetUrlTemplatePaikkaWithParams() {
        Map<String, String> params = Map.of("language", "fi", "limit", "5");
        GeocodeService service = new GeocodeService("Paikka", "https://geo.example.com", true, 0, null, null, GeocoderType.PAIKKA, 1, params);
        String template = service.getUrlTemplate();
        
        assertEquals("https://geo.example.com/api/v1/reverse?lat={lat}&lon={lng}&lang=fi&limit=5", template);
    }

    @Test
    void testGetUrlTemplateGeoApify() {
        Map<String, String> params = Map.of("apiKey", "test-key-123", "language", "en");
        GeocodeService service = new GeocodeService("GeoApify", "https://api.geoapify.com", true, 0, null, null, GeocoderType.GEO_APIFY, 1, params);
        String template = service.getUrlTemplate();
        
        assertEquals("https://api.geoapify.com/v1/geocode/reverse?lat={lat}&lon={lng}&apiKey=test-key-123&lang=en", template);
    }

    @Test
    void testGetUrlTemplateNominatim() {
        GeocodeService service = new GeocodeService("Nominatim", "https://nominatim.openstreetmap.org", true, 0, null, null, GeocoderType.NOMINATIM, 1, Map.of());
        String template = service.getUrlTemplate();
        assertEquals("https://nominatim.openstreetmap.org/reverse?format=geocodejson&lat={lat}&lon={lng}", template);
    }

    @Test
    void testGetUrlTemplateGeocodeJson() {
        String customUrl = "https://my-custom-service.com/api?x={lat}&y={lng}";
        GeocodeService service = new GeocodeService("Generic", customUrl, true, 0, null, null, GeocoderType.GEOCODE_JSON, 1, Map.of());
        String template = service.getUrlTemplate();
        assertEquals(customUrl, template);
    }
}