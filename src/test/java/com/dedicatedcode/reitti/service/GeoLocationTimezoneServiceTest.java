package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

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
        SignificantPlace newYork = SignificantPlace.create(40.7128, -74.0060);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(newYork);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("America/New_York"), timezone.get());
    }

    @Test
    void testTimezoneForLondon() {
        // London, UK - Greenwich Mean Time
        SignificantPlace london = SignificantPlace.create(51.5074, -0.1278);
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(london);
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("Europe/London"), timezone.get());
    }

    @Test
    void testTimezoneForTokyo() {
        // Tokyo, Japan - Japan Standard Time
        SignificantPlace tokyo = SignificantPlace.create(35.6762, 139.6503);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(tokyo);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("Asia/Tokyo"), timezone.get());
    }

    @Test
    void testTimezoneForSydney() {
        // Sydney, Australia - Australian Eastern Time
        SignificantPlace sydney = SignificantPlace.create(-33.8688, 151.2093);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(sydney);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("Australia/Sydney"), timezone.get());
    }

    @Test
    void testTimezoneForSaoPaulo() {
        // São Paulo, Brazil - Brasília Time
        SignificantPlace saoPaulo = SignificantPlace.create(-23.5505, -46.6333);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(saoPaulo);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("America/Sao_Paulo"), timezone.get());
    }

    @Test
    void testTimezoneForAntarctica() {
        // McMurdo Station, Antarctica - Multiple timezones possible
        // This location can have different timezone interpretations
        SignificantPlace mcmurdo = SignificantPlace.create(-77.8419, 166.6863);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(mcmurdo);
        
        assertTrue(timezone.isPresent());
        // Antarctica/McMurdo is linked to Pacific/Auckland
        assertTrue(timezone.get().equals(ZoneId.of("Antarctica/McMurdo")) || 
                  timezone.get().equals(ZoneId.of("Pacific/Auckland")));
    }

    @Test
    void testTimezoneForNorthPole() {
        // North Pole - Ambiguous timezone area
        SignificantPlace northPole = SignificantPlace.create(90.0, 0.0);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(northPole);
        
        // At the North Pole, timezone is ambiguous, but service should return something
        assertTrue(timezone.isPresent());
    }

    @Test
    void testTimezoneForInternationalDateLine() {
        // Location near International Date Line - Fiji
        SignificantPlace fiji = SignificantPlace.create(-18.1248, 178.4501);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(fiji);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("Pacific/Fiji"), timezone.get());
    }

    @Test
    void testTimezoneForEquator() {
        // Location on the Equator - Quito, Ecuador
        SignificantPlace quito = SignificantPlace.create(-0.1807, -78.4678);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(quito);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("America/Guayaquil"), timezone.get());
    }

    @Test
    void testTimezoneForPrimeMeridian() {
        // Location on Prime Meridian - Greenwich, London
        SignificantPlace greenwich = SignificantPlace.create(51.4769, 0.0005);
        
        Optional<ZoneId> timezone = geoLocationTimezoneService.getTimezone(greenwich);
        
        assertTrue(timezone.isPresent());
        assertEquals(ZoneId.of("Europe/London"), timezone.get());
    }
}
