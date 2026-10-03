package com.dedicatedcode.reitti.service.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypesTest {

    @Test
    void detectsSupportedImageFormats() {
        assertThat(ImageTypes.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})).contains("image/png");
        assertThat(ImageTypes.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains("image/jpeg");
        assertThat(ImageTypes.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).contains("image/gif");
        assertThat(ImageTypes.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).contains("image/webp");
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageTypes.detect(null)).isEmpty();
        assertThat(ImageTypes.detect(new byte[0])).isEmpty();
        assertThat(ImageTypes.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageTypes.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageTypes.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }
}
