package com.dedicatedcode.reitti.service;

public final class TileUrlUtils {
    private TileUrlUtils() {
    }

    public static String extractTileExtension(String url) {
        String path = url.split("\\?", 2)[0];
        int placeholderIndex = path.indexOf("{y}");
        if (placeholderIndex < 0) {
            return "pbf";
        }

        int extensionStart = placeholderIndex + 3;
        while (extensionStart < path.length() && path.charAt(extensionStart) == '{') {
            int tokenEnd = path.indexOf('}', extensionStart);
            if (tokenEnd < 0) {
                break;
            }
            extensionStart = tokenEnd + 1;
        }
        if (path.startsWith("@2x", extensionStart)) {
            extensionStart += 3;
        }
        if (extensionStart >= path.length() || path.charAt(extensionStart) != '.') {
            return "pbf";
        }

        int extensionEnd = extensionStart + 1;
        while (extensionEnd < path.length() && Character.isLetterOrDigit(path.charAt(extensionEnd))) {
            extensionEnd++;
        }

        if (extensionEnd == extensionStart + 1) {
            return "pbf";
        }
        return path.substring(extensionStart + 1, extensionEnd);
    }
}
