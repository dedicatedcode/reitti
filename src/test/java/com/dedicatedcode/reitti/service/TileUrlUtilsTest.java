package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileUrlUtilsTest {

    @Test
    void extractsExtensionAfterRetinaSuffix() {
        assertEquals("png", TileUrlUtils.extractTileExtension("http://192.168.178.51:8925/{z}/{x}/{y}@2x.png"));
    }

    @Test
    void extractsExtensionAfterPlaceholderSuffix() {
        assertEquals("webp", TileUrlUtils.extractTileExtension("https://example.com/{z}/{x}/{y}{r}.webp"));
    }
}
