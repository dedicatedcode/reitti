package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    private final ImageTypeDetector detector = new ImageTypeDetector();

    @Test
    void detectsJpeg() throws IOException {
        assertThat(detect("data/images/pexels-waldemar-nowak-305041-910625.jpg"))
                .contains(ImageType.JPEG);
    }

    @Test
    void detectsPng() throws IOException {
        assertThat(detect("static/img/logo.png"))
                .contains(ImageType.PNG);
    }

    @Test
    void detectsGif() throws IOException {
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(detect(gif)).contains(ImageType.GIF);
    }

    @Test
    void detectsWebp() throws IOException {
        byte[] webp = new byte[]{
                'R', 'I', 'F', 'F', 0x1a, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };

        assertThat(detect(webp)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsHtml() throws IOException {
        assertThat(detect("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void rejectsSvg() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(detect(svg)).isEmpty();
    }

    @Test
    void rejectsUnknownContent() throws IOException {
        assertThat(detect(new byte[]{0x00, 0x01, 0x02, 0x03})).isEmpty();
    }

    private java.util.Optional<ImageType> detect(String classPathResource) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classPathResource).getInputStream()) {
            return detector.detectImage(inputStream);
        }
    }

    private java.util.Optional<ImageType> detect(byte[] content) throws IOException {
        return detector.detectImage(new java.io.ByteArrayInputStream(content));
    }
}
