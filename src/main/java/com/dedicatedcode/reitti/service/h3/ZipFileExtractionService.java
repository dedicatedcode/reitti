package com.dedicatedcode.reitti.service.h3;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipFileExtractionService {

    private static final int BUFFER_SIZE = 8192; // 8KB buffer

    public void extractZipStreaming(Path zipFile, Path targetDirectory) throws IOException {
        // 1. Convert the base target to an absolute, clean path immediately
        Path targetDirAbs = targetDirectory.toAbsolutePath().normalize();
        Files.createDirectories(targetDirAbs);

        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                // 2. Resolve the entry against the absolute base, then normalize
                Path targetPath = targetDirAbs.resolve(entry.getName()).normalize();

                // 3. Guard against Zip Slip attack (now safely compared as absolute paths)
                if (!targetPath.startsWith(targetDirAbs)) {
                    throw new IOException("Malicious ZIP entry detected outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    // Ensure parent directories exist for the file
                    Files.createDirectories(targetPath.getParent());

                    // Stream the entry directly to disk
                    try (OutputStream os = Files.newOutputStream(targetPath)) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}