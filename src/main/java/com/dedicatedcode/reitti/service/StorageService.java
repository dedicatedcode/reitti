package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Service
public class StorageService {
    private final String storagePath;
    private final Path root;

    public StorageService(@Value("${reitti.storage.path}") String storagePath) {
        this.storagePath = storagePath;
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
        Path path = Paths.get(storagePath);
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory '" + storagePath + "': " + e.getMessage(), e);
        }
        
        if (!Files.isWritable(path)) {
            throw new RuntimeException("Storage path '" + storagePath + "' is not writable. Please ensure the directory exists and the application has write permissions.");
        }
    }

    public void store(String itemName, InputStream content, long contentLength, String contentType) {
        Path filePath = resolve(itemName);
        try {
            Files.createDirectories(filePath.getParent());
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store item '" + itemName + "': " + e.getMessage(), e);
        }
    }

    public StorageContent read(String itemName) {
        Path filePath = resolve(itemName);
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
        return Files.exists(resolve(itemName));
    }

    public List<String> getChildren(String path) {
        Path basePath = resolve(path);
        if (!Files.isDirectory(basePath)) {
            return Collections.emptyList();
        }
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
        Path filePath = resolve(itemName);
        try {
            if (Files.exists(filePath)) {
                if (Files.isDirectory(filePath)) {
                    try (Stream<Path> paths = Files.walk(filePath)) {
                        paths.sorted(java.util.Comparator.reverseOrder())
                             .forEach(path -> {
                                 try {
                                     Files.delete(path);
                                 } catch (IOException e) {
                                     throw new RuntimeException("Failed to delete path '" + path + "': " + e.getMessage(), e);
                                 }
                             });
                    }
                } else {
                    // Delete single file
                    Files.delete(filePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove item '" + itemName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Item names are partly built from request data, so every access must stay strictly inside the storage root.
     */
    private Path resolve(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("Storage item name must not be empty");
        }
        Path resolved = Paths.get(storagePath, itemName).toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Storage item '" + itemName + "' is outside of the storage directory");
        }
        return resolved;
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
