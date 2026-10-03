package com.dedicatedcode.reitti.service.security;

import java.util.Optional;

/**
 * Detects raster image formats from their magic bytes, so that content fetched from remote servers is only stored
 * and served as an image if it really is one (never trusting the remote Content-Type).
 */
public final class ImageTypes {

    private ImageTypes() {
    }

    /**
     * @return the MIME type (image/png, image/jpeg, image/gif or image/webp) or empty if the data is not one of those
     */
    public static Optional<String> detect(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        if (startsWith(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(data, 0, 'G', 'I', 'F', '8', '7', 'a') || startsWith(data, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of("image/gif");
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
