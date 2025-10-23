package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {
    private final String storagePath;

    public StorageService(@Value("${reitti.storage.path}") String storagePath) {
        this.storagePath = storagePath;
        Path path = Paths.get(storagePath);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            if (!Files.isWritable(path)) {
                throw new RuntimeException("Storage path '" + storagePath + "' is not writable. Please ensure the directory exists and the application has write permissions.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage path '" + storagePath + "': " + e.getMessage(), e);
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
        //allow glob patterns for filename matching like /storage-path/itemName** AI!
        Path filePath = Paths.get(storagePath, itemName);
        return Files.exists(filePath);
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
