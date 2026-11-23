package com.dedicatedcode.reitti.model.extension;

import java.time.Instant;

public record ExtensionRegistration(
        Long id,
        String manifestId,
        String name,
        String version,
        String manifestUrl,
        String manifestJson,
        boolean enabled,
        String encryptedCredentials,
        long revision,
        Instant lastRefreshedAt,
        Boolean lastRefreshSuccess,
        String lastError) {

    public ExtensionRegistration withManifest(ExtensionManifest manifest, String manifestUrl, String manifestJson, Instant refreshedAt) {
        return new ExtensionRegistration(id, manifest.id(), manifest.name(), manifest.version(), manifestUrl,
                manifestJson, enabled, encryptedCredentials, revision + 1, refreshedAt, true, null);
    }

    public ExtensionRegistration withEnabled(boolean value) {
        return new ExtensionRegistration(id, manifestId, name, version, manifestUrl, manifestJson, value,
                encryptedCredentials, revision + 1, lastRefreshedAt, lastRefreshSuccess, lastError);
    }

    public ExtensionRegistration withCredentials(String value) {
        return new ExtensionRegistration(id, manifestId, name, version, manifestUrl, manifestJson, enabled,
                value, revision, lastRefreshedAt, lastRefreshSuccess, lastError);
    }
}
