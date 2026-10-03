package com.dedicatedcode.reitti.service.memory;

import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServicePathTest {

    @TempDir
    Path tempDir;

    private Path root;
    private StorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(tempDir.resolve("storage"));
        storageService = new StorageService(root.toString());
    }

    @Test
    void storesAndReadsItemsInsideTheRoot() throws IOException {
        storageService.store("memories/1/image.jpg", content("data"), 4, "image/jpeg");

        assertTrue(Files.exists(root.resolve("memories/1/image.jpg")));
        assertTrue(storageService.exists("memories/1/image.jpg"));
        try (InputStream in = storageService.read("memories/1/image.jpg").getInputStream()) {
            assertEquals("data", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // the cleanup job addresses items with a leading slash
        assertEquals(List.of("1"), storageService.getChildren("/memories"));
    }

    @Test
    void refusesToWriteOutsideTheRoot() {
        assertThrows(IllegalArgumentException.class, () -> storageService.store("../escape.txt", content("x"), 1, "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> storageService.store("memories/1/../../../escape.txt", content("x"), 1, "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> storageService.store("/../escape.txt", content("x"), 1, "text/plain"));

        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
    }

    @Test
    void refusesToReadOrRemoveOutsideTheRoot() throws IOException {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThrows(IllegalArgumentException.class, () -> storageService.read("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storageService.exists("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storageService.getChildren(".."));
        assertThrows(IllegalArgumentException.class, () -> storageService.remove("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storageService.remove("."));
        assertThrows(IllegalArgumentException.class, () -> storageService.remove(""));

        assertTrue(Files.exists(secret));
        assertTrue(Files.exists(root));
    }

    @Test
    void existsDoesNotInterpretGlobPatterns() {
        storageService.store("memories/1/image.jpg", content("data"), 4, "image/jpeg");

        assertFalse(storageService.exists("memories/1/*"));
        assertFalse(storageService.exists("**"));
        // used to be compiled as a glob and fail with an exception
        assertFalse(storageService.exists("memories/1/[unclosed"));
    }

    private static InputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
