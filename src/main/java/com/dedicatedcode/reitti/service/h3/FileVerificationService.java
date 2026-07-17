package com.dedicatedcode.reitti.service.h3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class FileVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FileVerificationService.class);

    private static final int BUFFER_SIZE = 8192;

    public boolean verifyChecksum(Path file, String expectedSha256) {
        if (file == null || !Files.exists(file)) {
            log.error("Verification failed: Target file does not exist.");
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

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
            log.error("Failed to read database package during checksum verification: {}", e.getMessage());
            return false;
        }
    }
}
