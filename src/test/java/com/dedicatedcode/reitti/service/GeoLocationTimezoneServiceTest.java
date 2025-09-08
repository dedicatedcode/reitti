package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GeoLocationTimezoneServiceTest {

    @InjectMocks
    private GeoLocationTimezoneService geoLocationTimezoneService;

    @BeforeEach
    void setUp() {
        // Initialize the service if needed
    }

    @Test
    void testTimezoneForNewYorkCity() {
        // New York City, USA - Eastern Time
        GeoPoint newYork = new GeoPoint(40.7128, -74.0060);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(newYork);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("America/New_York"), timezone);
    }

    @Test
    void testTimezoneForLondon() {
        // London, UK - Greenwich Mean Time
        GeoPoint london = new GeoPoint(51.5074, -0.1278);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(london);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("Europe/London"), timezone);
    }

    @Test
    void testTimezoneForTokyo() {
        // Tokyo, Japan - Japan Standard Time
        GeoPoint tokyo = new GeoPoint(35.6762, 139.6503);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(tokyo);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("Asia/Tokyo"), timezone);
    }

    @Test
    void testTimezoneForSydney() {
        // Sydney, Australia - Australian Eastern Time
        GeoPoint sydney = new GeoPoint(-33.8688, 151.2093);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(sydney);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("Australia/Sydney"), timezone);
    }

    @Test
    void testTimezoneForSaoPaulo() {
        // São Paulo, Brazil - Brasília Time
        GeoPoint saoPaulo = new GeoPoint(-23.5505, -46.6333);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(saoPaulo);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("America/Sao_Paulo"), timezone);
    }

    @Test
    void testTimezoneForAntarctica() {
        // McMurdo Station, Antarctica - Multiple timezones possible
        // This location can have different timezone interpretations
        GeoPoint mcmurdo = new GeoPoint(-77.8419, 166.6863);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(mcmurdo);
        
        assertNotNull(timezone);
        // Antarctica/McMurdo is linked to Pacific/Auckland
        assertTrue(timezone.equals(ZoneId.of("Antarctica/McMurdo")) || 
                  timezone.equals(ZoneId.of("Pacific/Auckland")));
    }

    @Test
    void testTimezoneForNorthPole() {
        // North Pole - Ambiguous timezone area
        GeoPoint northPole = new GeoPoint(90.0, 0.0);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(northPole);
        
        // At the North Pole, timezone is ambiguous, but service should return something
        assertNotNull(timezone);
    }

    @Test
    void testTimezoneForInternationalDateLine() {
        // Location near International Date Line - Fiji
        GeoPoint fiji = new GeoPoint(-18.1248, 178.4501);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(fiji);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("Pacific/Fiji"), timezone);
    }

    @Test
    void testTimezoneForEquator() {
        // Location on the Equator - Quito, Ecuador
        GeoPoint quito = new GeoPoint(-0.1807, -78.4678);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(quito);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("America/Guayaquil"), timezone);
    }

    @Test
    void testTimezoneForPrimeMeridian() {
        // Location on Prime Meridian - Greenwich, London
        GeoPoint greenwich = new GeoPoint(51.4769, 0.0005);
        
        ZoneId timezone = geoLocationTimezoneService.getTimezone(greenwich);
        
        assertNotNull(timezone);
        assertEquals(ZoneId.of("Europe/London"), timezone);
    }
}
