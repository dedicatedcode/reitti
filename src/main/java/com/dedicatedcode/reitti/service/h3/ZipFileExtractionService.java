package com.dedicatedcode.reitti.service.h3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class ZipFileExtractionService {

    private static final int BUFFER_SIZE = 8192;

    public void extractZipStreaming(Path zipFile, Path targetDirectory) throws IOException {
        Path targetDirAbs = targetDirectory.toAbsolutePath().normalize();
        Files.createDirectories(targetDirAbs);

        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDirAbs.resolve(entry.getName()).normalize();

                if (!targetPath.startsWith(targetDirAbs)) {
                    throw new IOException("Malicious ZIP entry detected outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());

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
