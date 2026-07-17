package com.dedicatedcode.reitti.service.h3;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class FileVerificationService {

    private static final int BUFFER_SIZE = 8192; // 8KB buffer to conserve heap memory

    /**
     * Streams a downloaded file and calculates its SHA-256 checksum to verify integrity.
     * 
     * @param file The downloaded ZIP package
     * @param expectedSha256 The hex-encoded checksum provided by the manifest
     * @return true if the calculated hash matches the expected hash
     */
    public boolean verifyChecksum(Path file, String expectedSha256) {
        if (file == null || !Files.exists(file)) {
            System.err.println("Verification failed: Target file does not exist.");
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Stream the file progressively
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead); // Feed chunk into MD5 digest
                }
            }

            // Convert byte array to hexadecimal format
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String calculatedSha256 = hexString.toString();
            return calculatedSha256.equalsIgnoreCase(expectedSha256);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is not available in this JVM context.", e);
        } catch (IOException e) {
            System.err.println("Failed to read database package during checksum verification: " + e.getMessage());
            return false;
        }
    }
}