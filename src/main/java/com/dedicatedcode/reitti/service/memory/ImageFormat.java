package com.dedicatedcode.reitti.service.memory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Image formats that may be stored for and served from memories. Anything else (html, svg, ...) is refused, since
 * stored files are served from the application origin.
 */
public enum ImageFormat {
    JPEG("jpg", "image/jpeg", "jpg", "jpeg"),
    PNG("png", "image/png", "png"),
    GIF("gif", "image/gif", "gif"),
    WEBP("webp", "image/webp", "webp"),
    AVIF("avif", "image/avif", "avif"),
    HEIC("heic", "image/heic", "heic", "heif");

    /** Enough bytes to identify every format above. */
    public static final int HEADER_LENGTH = 64;

    private static final Set<String> AVIF_BRANDS = Set.of("avif", "avis");
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1");

    private final String extension;
    private final String mediaType;
    private final Set<String> knownExtensions;

    ImageFormat(String extension, String mediaType, String... knownExtensions) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.knownExtensions = Set.of(knownExtensions);
    }

    public String getExtension() {
        return extension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public static Optional<ImageFormat> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.knownExtensions.contains(ext)).findFirst();
    }

    /**
     * Identifies the format from the magic bytes at the start of the file.
     */
    public static Optional<ImageFormat> detect(byte[] header) {
        if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (ascii(header, 0, 6).equals("GIF87a") || ascii(header, 0, 6).equals("GIF89a")) {
            return Optional.of(GIF);
        }
        if (ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(header, 4, 4).equals("ftyp")) {
            return detectIsoBmff(header);
        }
        return Optional.empty();
    }

    // AVIF and HEIC are ISO-BMFF containers: an 'ftyp' box listing the major brand, minor version and compatible brands
    private static Optional<ImageFormat> detectIsoBmff(byte[] header) {
        long boxSize = ((header[0] & 0xFFL) << 24) | ((header[1] & 0xFFL) << 16) | ((header[2] & 0xFFL) << 8) | (header[3] & 0xFFL);
        int end = (int) Math.min(boxSize, header.length);
        boolean heic = false;
        for (int offset = 8; offset + 4 <= end; offset += 4) {
            if (offset == 12) {
                continue; // minor version
            }
            String brand = ascii(header, offset, 4);
            if (AVIF_BRANDS.contains(brand)) {
                return Optional.of(AVIF);
            }
            heic |= HEIC_BRANDS.contains(brand);
        }
        return heic ? Optional.of(HEIC) : Optional.empty();
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

    private static String ascii(byte[] data, int offset, int length) {
        if (data.length < offset + length) {
            return "";
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }
}
