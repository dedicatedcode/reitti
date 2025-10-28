package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

@Service
public class StorageService {
    private final String storagePath;

    public StorageService(@Value("${reitti.storage.path}") String storagePath) {
        this.storagePath = storagePath;
        Path path = Paths.get(storagePath);
        if (!Files.isWritable(path)) {
            throw new RuntimeException("Storage path '" + storagePath + "' is not writable. Please ensure the directory exists and the application has write permissions.");
        }
    }

    public void store(String itemName, InputStream content, long contentLength, String contentType) {
        Path filePath = Paths.get(storagePath, itemName);
        try {
            Files.createDirectories(filePath.getParent());
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store item '" + itemName + "': " + e.getMessage(), e);
        }
    }

    public StorageContent read(String itemName) {
        Path filePath = Paths.get(storagePath, itemName);
        try {
            InputStream inputStream = Files.newInputStream(filePath);
            String contentType = Files.probeContentType(filePath);
            long contentLength = Files.size(filePath);
            return new StorageContent(inputStream, contentType, contentLength);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read item '" + itemName + "': " + e.getMessage(), e);
        }
    }

    public boolean exists(String itemName) {
        Path basePath = Paths.get(storagePath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + itemName);
        try (Stream<Path> paths = Files.walk(basePath)) {
            return paths
                    .map(basePath::relativize)
                    .anyMatch(matcher::matches);
        } catch (IOException e) {
            return false;
        }
    }

    public List<String> getDirectories(String path) {
        Path basePath = Paths.get(storagePath, path);
        try (Stream<Path> paths = Files.walk(basePath, 1)) {
            return paths
                    .map(basePath::relativize)
                    .map(Path::toString)
                    .filter(istr -> !istr.isEmpty() && !istr.equals(".") && !istr.equals(".."))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read item '" + path + "': " + e.getMessage(), e);
        }
    }

    public void remove(String itemName) {
        Path filePath = Paths.get(storagePath, itemName);
        //delete filePath nd if it is a directory all of its content AI!
    }

    public static class StorageContent {
        private final InputStream inputStream;
        private final String contentType;
        private final Long contentLength;

        public StorageContent(InputStream inputStream, String contentType, Long contentLength) {
            this.inputStream = inputStream;
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getContentType() {
            return contentType;
        }

        public Long getContentLength() {
            return contentLength;
        }
    }
}
