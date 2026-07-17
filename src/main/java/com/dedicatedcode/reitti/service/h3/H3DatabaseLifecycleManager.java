package com.dedicatedcode.reitti.service.h3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class H3DatabaseLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(H3DatabaseLifecycleManager.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RocksDBH3Service rocksDbService;
    private final ZipFileExtractionService extractorService;
    private final H3IndexDownloadService downloadService;
    private final FileVerificationService verificationService;

    private final Path rootDbDir;
    private final Path localManifestPath;
    private final Path tempZipPath;

    private final String remoteManifestUrl;

    public H3DatabaseLifecycleManager(RocksDBH3Service rocksDbService,
                                      ZipFileExtractionService extractorService,
                                      H3IndexDownloadService downloadService,
                                      FileVerificationService verificationService,
                                      @Value("${reitti.h3.root-dir}") String h3RootDir,
                                      @Value("${reitti.h3.tmp-zip-path}") String h3TmpZipPah,
                                      @Value("${reitti.h3.manifest-url}") String manifestDownloadUrl) {
        this.downloadService = downloadService;
        this.verificationService = verificationService;
        this.rootDbDir = Path.of(h3RootDir);
        this.rocksDbService = rocksDbService;
        this.extractorService = extractorService;
        this.localManifestPath = rootDbDir.resolve("local-manifest.json");
        this.tempZipPath = Path.of(h3TmpZipPah);
        this.remoteManifestUrl = manifestDownloadUrl;
        checkAndPrepareDatabase();
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndPrepareDatabase() {
        try {
            Files.createDirectories(rootDbDir);

            H3Manifest remoteManifest = fetchRemoteManifest();
            String remoteVersion = remoteManifest.getVersion();

            Path targetVersionDir = rootDbDir.resolve("version_" + remoteVersion);
            Path tempExtractionDir = rootDbDir.resolve("version_" + remoteVersion + "_temp");

            if (Files.exists(targetVersionDir)) {
                log.info("H3 database is up to date at version: {}", remoteVersion);
                return;
            }

            log.info("New database version detected: {}. Commencing background upgrade...", remoteVersion);

            downloadService.downloadDatabaseWithResume(remoteManifest.getDownloadUrl(), tempZipPath);
            boolean checksumMatches = verificationService.verifyChecksum(tempZipPath, remoteManifest.getSha256());
            if (!checksumMatches) {
                throw new IllegalStateException("Checksum mismatch");
            }

            Files.createDirectories(tempExtractionDir);
            extractorService.extractZipStreaming(tempZipPath, tempExtractionDir);

            Files.move(tempExtractionDir, targetVersionDir);

            rocksDbService.hotSwapDatabase(targetVersionDir);

            saveLocalManifest(remoteManifest);

            Files.deleteIfExists(tempZipPath);
            log.info("Database successfully updated to version: {}", remoteVersion);

        } catch (Exception e) {
            log.error("Critical failure during H3 database upgrade lifecycle: {}", e.getMessage(), e);
        }
    }

    private H3Manifest fetchRemoteManifest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(remoteManifestUrl)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), H3Manifest.class);
    }

    private void saveLocalManifest(H3Manifest manifest) throws IOException {
        objectMapper.writeValue(localManifestPath.toFile(), manifest);
    }
}
