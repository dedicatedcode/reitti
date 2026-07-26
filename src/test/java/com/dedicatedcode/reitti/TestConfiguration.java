package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeResult;
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import com.dedicatedcode.reitti.service.geocoding.GeocodeServiceManager;
import com.dedicatedcode.reitti.service.h3.FileVerificationService;
import com.dedicatedcode.reitti.service.h3.H3IndexDownloadService;
import com.dedicatedcode.reitti.service.h3.H3Manifest;
import com.dedicatedcode.reitti.service.h3.H3ManifestDownloadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class TestConfiguration {
    private final AtomicInteger geocodes = new AtomicInteger(1);
    private final ObjectMapper objectMapper;

    public TestConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public GeocodeServiceManager geocodeServiceManager() {
        return new GeocodeServiceManager() {
            @Override
            public Optional<GeocodeResult> reverseGeocode(SignificantPlace significantPlace, boolean recordResponse) {
                String label = significantPlace.getLatitudeCentroid() + "," + significantPlace.getLongitudeCentroid();
                return Optional.of(new GeocodeResult(label, "Test Street " + geocodes.getAndIncrement(), "1", "Test City", "12345","Test District", "de", SignificantPlace.PlaceType.OTHER));
            }

            @Override
            public Map<String, Object> test(GeocodeService service, double testLat, double testLng) {
                return null;
            }

            @Override
            public Map<GeocoderType, List<GeocodeResult>> reverseGeocodeAll(SignificantPlace significantPlace) {
                return Map.of();
            }
        };
    }

    @Bean
    public H3ManifestDownloadService h3ManifestDownloadService() {
        return new H3ManifestDownloadService(null) {
            @Override
            public H3Manifest fetchRemoteManifest() throws IOException, InterruptedException {
                return objectMapper.readValue(this.getClass().getResourceAsStream("/data/h3-index-sh/manifest.json"), H3Manifest.class);
            }
        };
    }
    @Bean
    public H3IndexDownloadService downloadService() {
        return new H3IndexDownloadService() {
            @Override
            public void downloadDatabaseWithResume(String downloadUrl, Path targetFile) throws IOException {
                if (targetFile.getParent() != null) {
                    Files.createDirectories(targetFile.getParent());
                }
                StandardOpenOption[] writeOptions = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

                try (InputStream bodyStream = this.getClass().getResourceAsStream("/data/h3-index-sh/h3-rocksdb-2026-07-26-v1.zip");
                     ReadableByteChannel readableChannel = Channels.newChannel(bodyStream);
                     FileChannel fileChannel = FileChannel.open(targetFile, writeOptions)) {

                    fileChannel.transferFrom(readableChannel, 0, Long.MAX_VALUE);
                }
            }
        };
    }

    @Bean
    public FileVerificationService fileVerificationService() {
        return new FileVerificationService() {
            @Override
            public boolean verifyChecksum(Path file, String expectedSha256) {
                return true;
            }
        };
    }
}
