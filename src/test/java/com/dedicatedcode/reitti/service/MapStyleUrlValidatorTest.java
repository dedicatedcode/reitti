package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MapStyleUrlValidatorTest {

    private final MapStyleUrlValidator validator = new MapStyleUrlValidator(mock(I18nService.class));

    @Test
    void acceptsHttpUrlsAndTemplatesRegardlessOfNetworkLocation() {
        assertDoesNotThrow(() -> validator.requireHttpUrl("https://tile.openstreetmap.org/0/0/0.png", "Tile URL"));
        assertDoesNotThrow(() -> validator.requireHttpUrl("http://localhost:8080/style.json", "Style URL"));
        assertDoesNotThrow(() -> validator.requireHttpTemplate("http://192.168.1.10/{z}/{x}/{y}.png", "Tile URL template"));
    }

    @Test
    void rejectsUnsupportedSchemesAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class, () -> validator.requireHttpUrl("file:///etc/passwd", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requireHttpUrl("https://user:secret@example.com/tile.png", "Tile URL"));
    }
}
