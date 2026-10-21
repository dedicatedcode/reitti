package com.dedicatedcode.reitti.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class ImageTypeDetector {
    private static final int PREFIX_LENGTH = 64 * 1024;

    private final Tika tika = new Tika();

    public Optional<ImageType> detectImage(InputStream inputStream) throws IOException {
        return ImageType.fromContentType(detectContentType(inputStream));
    }

    public Optional<ImageType> detectImage(Path path) throws IOException {
        return ImageType.fromContentType(detectContentType(path));
    }

    public Optional<ImageType> detectImage(String contentType) {
        return ImageType.fromContentType(contentType);
    }

    public String detectContentType(InputStream inputStream) throws IOException {
        byte[] prefix = inputStream.readNBytes(PREFIX_LENGTH);
        return tika.detect(prefix);
    }

    public String detectContentType(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return detectContentType(inputStream);
        }
    }
}
