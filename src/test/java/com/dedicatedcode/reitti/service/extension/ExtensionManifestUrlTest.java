package com.dedicatedcode.reitti.service.extension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionManifestUrlTest {

    @Test
    void appendsTheConventionalManifestNameToHostsAndDirectories() {
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com"))
                .isEqualTo("https://example.com/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com/"))
                .isEqualTo("https://example.com/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com/prefix"))
                .isEqualTo("https://example.com/prefix/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com/prefix/"))
                .isEqualTo("https://example.com/prefix/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("http://overlay-extension:8090"))
                .isEqualTo("http://overlay-extension:8090/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("http://[::1]:8090"))
                .isEqualTo("http://[::1]:8090/reitti-extension.json");
    }

    @Test
    void keepsFullManifestUrls() {
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com/custom.json"))
                .isEqualTo("https://example.com/custom.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("https://example.com/custom.JSON"))
                .isEqualTo("https://example.com/custom.JSON");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("http://overlay-extension:8090/manifest.json"))
                .isEqualTo("http://overlay-extension:8090/manifest.json");
    }

    @Test
    void assumesHttpWhenTheSchemeIsMissing() {
        assertThat(ExtensionCatalogService.normalizeManifestUrl("overlay-extension:8090"))
                .isEqualTo("http://overlay-extension:8090/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("192.168.2.200:8094/prefix"))
                .isEqualTo("http://192.168.2.200:8094/prefix/reitti-extension.json");
        assertThat(ExtensionCatalogService.normalizeManifestUrl("example.com/custom.json"))
                .isEqualTo("http://example.com/custom.json");
    }

    @Test
    void normalizesTheSchemeCase() {
        assertThat(ExtensionCatalogService.normalizeManifestUrl("HTTPS://example.com"))
                .isEqualTo("https://example.com/reitti-extension.json");
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> ExtensionCatalogService.normalizeManifestUrl(null))
                .isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> ExtensionCatalogService.normalizeManifestUrl("   "))
                .isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> ExtensionCatalogService.normalizeManifestUrl("ftp://example.com/manifest.json"))
                .isInstanceOf(ExtensionException.class)
                .hasMessageContaining("HTTP or HTTPS");
        assertThatThrownBy(() -> ExtensionCatalogService.normalizeManifestUrl("http://example.com/\n"))
                .isInstanceOf(ExtensionException.class);
    }
}
