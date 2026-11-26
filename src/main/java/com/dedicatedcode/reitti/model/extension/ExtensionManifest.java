package com.dedicatedcode.reitti.model.extension;

import java.util.List;

public record ExtensionManifest(
        String id,
        String name,
        String version,
        String description,
        String iconAssetId,
        String iconUrl,
        List<ExtensionLayer> layers,
        List<String> unsupportedLayers) {
}
