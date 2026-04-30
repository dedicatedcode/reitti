package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileProxySignatureServiceTest {

    @Test
    void validatesSignaturesCreatedWithSameConfiguredSecret() {
        TileProxySignatureService signer = new TileProxySignatureService("stable-secret");
        TileProxySignatureService verifier = new TileProxySignatureService("stable-secret");

        String value = "https://tiles.example.test/{z}/{x}/{y}.pbf";
        assertTrue(verifier.isValid(value, signer.sign(value)));
    }

    @Test
    void rejectsSignaturesCreatedWithDifferentSecret() {
        TileProxySignatureService signer = new TileProxySignatureService("first-secret");
        TileProxySignatureService verifier = new TileProxySignatureService("second-secret");

        String value = "https://tiles.example.test/{z}/{x}/{y}.pbf";
        assertFalse(verifier.isValid(value, signer.sign(value)));
    }
}
