package com.dedicatedcode.reitti.service.memory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFormatTest {

    @Test
    void detectsSupportedImagesByContent() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)));
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00)));
        assertEquals(Optional.of(ImageFormat.GIF), ImageFormat.detect(ascii("GIF89a....")));
        assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.detect(ascii("RIFF\0\0\0\0WEBPVP8 ")));
        assertEquals(Optional.of(ImageFormat.AVIF), ImageFormat.detect(ftyp("avif", "mif1", "miaf")));
        assertEquals(Optional.of(ImageFormat.AVIF), ImageFormat.detect(ftyp("mif1", "avif")));
        assertEquals(Optional.of(ImageFormat.HEIC), ImageFormat.detect(ftyp("heic", "mif1", "heic")));
    }

    @Test
    void rejectsEverythingElse() {
        assertEquals(Optional.empty(), ImageFormat.detect(ascii("<html><script>alert(1)</script></html>")));
        assertEquals(Optional.empty(), ImageFormat.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>")));
        assertEquals(Optional.empty(), ImageFormat.detect(ascii("<?xml version=\"1.0\"?><svg/>")));
        assertEquals(Optional.empty(), ImageFormat.detect(ftyp("isom", "mp41")));
        assertEquals(Optional.empty(), ImageFormat.detect(new byte[0]));
        assertEquals(Optional.empty(), ImageFormat.detect(bytes(0xFF, 0xD8)));
    }

    @Test
    void mapsOnlyImageExtensions() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.fromFilename("a.JPG"));
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.fromFilename("a.jpeg"));
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.fromFilename("a.png"));
        assertEquals(Optional.empty(), ImageFormat.fromFilename("a.html"));
        assertEquals(Optional.empty(), ImageFormat.fromFilename("a.svg"));
        assertEquals(Optional.empty(), ImageFormat.fromFilename("a.png.html"));
        assertEquals(Optional.empty(), ImageFormat.fromFilename("noextension"));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] ftyp(String majorBrand, String... compatibleBrands) {
        int size = 16 + 4 * compatibleBrands.length;
        StringBuilder box = new StringBuilder();
        box.append((char) (size >>> 24)).append((char) ((size >>> 16) & 0xFF)).append((char) ((size >>> 8) & 0xFF)).append((char) (size & 0xFF));
        box.append("ftyp").append(majorBrand).append("\0\0\0\0");
        for (String brand : compatibleBrands) {
            box.append(brand);
        }
        box.append("\0\0\0\u0008free");
        return ascii(box.toString());
    }
}
