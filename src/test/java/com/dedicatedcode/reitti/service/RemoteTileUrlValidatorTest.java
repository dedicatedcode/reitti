package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteTileUrlValidatorTest {

    @Test
    void acceptsPublicHttpUrlsAndTemplates() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(false);

        assertDoesNotThrow(() -> validator.requirePublicHttpUrl("https://tile.openstreetmap.org/0/0/0.png", "Tile URL"));
        assertDoesNotThrow(() -> validator.requirePublicHttpTemplate("https://tile.openstreetmap.org/{z}/{x}/{y}.png", "Tile URL template"));
    }

    @Test
    void rejectsPrivateAndLocalTargetsByDefault() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(false);

        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("http://localhost:8080/test", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("http://127.0.0.1/test", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("http://10.0.0.10/test", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("http://192.168.1.10/test", "Tile URL"));
    }

    @Test
    void acceptsPrivateAndLocalTargetsWhenOnlyValidatingUrlShape() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(false);

        assertDoesNotThrow(() -> validator.requireHttpUrl("http://localhost:8080/style.json", "Style URL"));
        assertDoesNotThrow(() -> validator.requireHttpTemplate("http://192.168.1.10/{z}/{x}/{y}.png", "Tile URL template"));
    }

    @Test
    void rejectsUnsupportedSchemesAndEmbeddedCredentials() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(false);

        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("file:///etc/passwd", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requirePublicHttpUrl("https://user:secret@example.com/tile.png", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requireHttpUrl("file:///etc/passwd", "Tile URL"));
        assertThrows(IllegalArgumentException.class, () -> validator.requireHttpUrl("https://user:secret@example.com/tile.png", "Tile URL"));
    }

    @Test
    void canProxyLocalUrlsForInternalDeployments() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(true);

        assertDoesNotThrow(() -> validator.requirePublicHttpUrl("http://127.0.0.1/test", "Tile URL"));
    }

    @Test
    void detectsPrivateAndLocalHttpUrlsIndependentlyOfProxySetting() {
        RemoteTileUrlValidator validator = new RemoteTileUrlValidator(true);

        assertTrue(validator.isValidLocalUrl("http://127.0.0.1/test"));
        assertTrue(validator.isValidLocalUrl("http://192.168.1.10/test"));
        assertTrue(validator.isValidLocalTemplate("http://192.168.1.10/{z}/{x}/{y}.png"));
        assertFalse(validator.isValidLocalUrl("https://tile.openstreetmap.org/0/0/0.png"));
    }
}
