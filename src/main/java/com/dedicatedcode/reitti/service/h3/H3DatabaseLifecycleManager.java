package com.dedicatedcode.reitti.service.h3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@DisallowConcurrentExecution
public class H3DatabaseLifecycleManager implements Job {

    private static final Logger log = LoggerFactory.getLogger(H3DatabaseLifecycleManager.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RocksDBH3Service rocksDbService;
    private final ZipFileExtractionService extractorService;
    private final H3IndexDownloadService downloadService;
    private final FileVerificationService verificationService;
    private final JdbcTemplate jdbcTemplate;

    private final Path rootDbDir;
    private final Path localManifestPath;
    private final Path tempZipPath;

    private final String remoteManifestUrl;


    public H3DatabaseLifecycleManager(RocksDBH3Service rocksDbService,
                                      ZipFileExtractionService extractorService,
                                      H3IndexDownloadService downloadService,
                                      FileVerificationService verificationService,
                                      JdbcTemplate jdbcTemplate,
                                      @Value("${reitti.h3.root-dir}") String h3RootDir,
                                      @Value("${reitti.h3.tmp-zip-path}") String h3TmpZipPah,
                                      @Value("${reitti.h3.manifest-url}") String manifestDownloadUrl) {
        this.downloadService = downloadService;
        this.verificationService = verificationService;
        this.jdbcTemplate = jdbcTemplate;
        this.rootDbDir = Path.of(h3RootDir);
        this.rocksDbService = rocksDbService;
        this.extractorService = extractorService;
        this.localManifestPath = rootDbDir.resolve("local-manifest.json");
        this.tempZipPath = Path.of(h3TmpZipPah);
        this.remoteManifestUrl = manifestDownloadUrl;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
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
            loadOsmNames(targetVersionDir.resolve("osm_names.tsv"));
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

    private void loadOsmNames(Path tsvFilePath) throws Exception {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS public.osm_names (
                    osm_id bigint,
                    osm_type character(1),
                    all_names jsonb
                );
                """;

        String createBtreeIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_osm_names_id_type
                ON public.osm_names (osm_id, osm_type);
                """;

        // 🌟 ADDED: GIN Index for internal JSONB key/value lookups
        String createGinIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_osm_names_all_names_gin
                ON public.osm_names USING gin (all_names);
                """;

        String copySql = "COPY public.osm_names (osm_id, osm_type, all_names) FROM STDIN WITH (FORMAT text, DELIMITER '\t', NULL '')";

        log.info("Ensuring target table exists...");
        jdbcTemplate.execute(createTableSql);

        log.info("Streaming bulk data via native PostgreSQL COPY protocol...");
        jdbcTemplate.execute((java.sql.Connection con) -> {
            BaseConnection pgTargetConnection = con.unwrap(BaseConnection.class);
            CopyManager copyManager = new CopyManager(pgTargetConnection);
            try (BufferedReader reader = Files.newBufferedReader(tsvFilePath)) {
                copyManager.copyIn(copySql, reader);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // Build indexes AFTER copy finishes
        log.info("Building B-Tree lookup index...");
        jdbcTemplate.execute(createBtreeIndexSql);

        log.info("Building GIN index on jsonb names (this may take a moment)...");
        jdbcTemplate.execute(createGinIndexSql);

        log.info("Bulk data ingestion and indexing completed successfully.");

    }
}
