package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.CoverageContinent;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JdbcPropertyService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DisallowConcurrentExecution
public class H3DatabaseLifecycleManager implements Job {

    private static final Logger log = LoggerFactory.getLogger(H3DatabaseLifecycleManager.class);
    private static final String HIERARCHY_VERSION_PROPERTY = "h3.hierarchy.version";
    private static final String HIERARCHY_INGESTED_PROPERTY = "h3.hierarchy.ingested";
    private static final String STAGING_TABLE = "coverage_region_ingest";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RocksDBH3Service rocksDbService;
    private final ZipFileExtractionService extractorService;
    private final H3ManifestDownloadService manifestDownloadService;
    private final H3IndexDownloadService downloadService;
    private final FileVerificationService verificationService;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcPropertyService propertyService;
    private final JobMetadataRepository jobMetadataRepository;
    private final TransactionTemplate transactionTemplate;

    private final Path rootDbDir;
    private final Path localManifestPath;
    private final Path tempZipPath;

    private final JobSchedulingService jobSchedulingService;
    private final JobDetail h3RecalculationJob;

    public H3DatabaseLifecycleManager(RocksDBH3Service rocksDbService,
                                      ZipFileExtractionService extractorService,
                                      H3ManifestDownloadService manifestDownloadService,
                                      H3IndexDownloadService downloadService,
                                      FileVerificationService verificationService,
                                      JdbcTemplate jdbcTemplate,
                                      JdbcPropertyService propertyService,
                                      JobMetadataRepository jobMetadataRepository,
                                      PlatformTransactionManager transactionManager,
                                      @Value("${reitti.h3.root-dir}") String h3RootDir,
                                      @Value("${reitti.h3.tmp-zip-path}") String h3TmpZipPah,
                                      JobSchedulingService jobSchedulingService,
                                      @Qualifier("h3RecalculationJob") JobDetail h3RecalculationJob) {
        this.manifestDownloadService = manifestDownloadService;
        this.downloadService = downloadService;
        this.verificationService = verificationService;
        this.jdbcTemplate = jdbcTemplate;
        this.propertyService = propertyService;
        this.jobMetadataRepository = jobMetadataRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rootDbDir = Path.of(h3RootDir);
        this.rocksDbService = rocksDbService;
        this.extractorService = extractorService;
        this.jobSchedulingService = jobSchedulingService;
        this.h3RecalculationJob = h3RecalculationJob;
        this.localManifestPath = rootDbDir.resolve("local-manifest.json");
        this.tempZipPath = Path.of(h3TmpZipPah);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("Starting H3 database upgrade lifecycle...");
            Files.createDirectories(rootDbDir);

            H3Manifest remoteManifest = manifestDownloadService.fetchRemoteManifest();
            String remoteVersion = remoteManifest.getVersion();

            Path targetVersionDir = rootDbDir.resolve("version_" + remoteVersion);
            Path tempExtractionDir = rootDbDir.resolve("version_" + remoteVersion + "_temp");

            if (Files.exists(targetVersionDir)) {
                log.info("H3 database is up to date at version: {}. Will load it now.", remoteVersion);
                rocksDbService.hotSwapDatabase(targetVersionDir);
                ingestRegionHierarchy(targetVersionDir, remoteVersion);
                rocksDbService.deleteOldVersions();
            } else {
                log.info("New database version detected: {}. Commencing background upgrade...", remoteVersion);

                downloadService.downloadDatabaseWithResume(remoteManifest.getDownloadUrl(), tempZipPath);
                boolean checksumMatches = verificationService.verifyChecksum(tempZipPath, remoteManifest.getSha256());
                if (!checksumMatches) {
                    throw new IllegalStateException("Checksum mismatch");
                }

                Files.createDirectories(tempExtractionDir);
                extractorService.extractZipStreaming(tempZipPath, tempExtractionDir);

                Files.move(tempExtractionDir, targetVersionDir);

                loadOsmNames(targetVersionDir.resolve("osm_names.tsv"));

                rocksDbService.hotSwapDatabase(targetVersionDir);
                ingestRegionHierarchy(targetVersionDir, remoteVersion);
                saveLocalManifest(remoteManifest);
                rocksDbService.deleteOldVersions();

                Files.deleteIfExists(tempZipPath);
                log.info("Database successfully updated to version: {}", remoteVersion);
            }

            this.jobSchedulingService.enqueueTask(h3RecalculationJob,
                                                   new H3RecalculationJob.TaskData(),
                                                    JobSchedulingService.Metadata.builder()
                                                          .friendlyName("Recalculating H3 location cells")
                                                          .jobType(JobType.H3_RECALCULATION)
                                                          .build());

        } catch (Exception e) {
            log.error("Critical failure during H3 database upgrade lifecycle: {}", e.getMessage(), e);
        }
    }

    private void saveLocalManifest(H3Manifest manifest) throws IOException {
        objectMapper.writeValue(localManifestPath.toFile(), manifest);
    }

    private void ingestRegionHierarchy(Path versionDir, String version) {
        Path tsvFilePath = versionDir.resolve("region_hierarchy.tsv");
        if (!Files.isRegularFile(tsvFilePath)) {
            log.warn("Bundle {} has no region_hierarchy.tsv; coverage hierarchy will be unavailable until a bundle with it arrives", version);
            return;
        }
        boolean alreadyIngested = propertyService.getProperty(HIERARCHY_INGESTED_PROPERTY)
                .filter(version::equals)
                .isPresent();
        if (alreadyIngested && coverageRegionCount() > 0) {
            log.debug("Coverage hierarchy already ingested for pack version {}", version);
            return;
        }
        try {
            loadRegionHierarchy(tsvFilePath);
            propertyService.setProperty(HIERARCHY_INGESTED_PROPERTY, version);
            propertyService.setProperty(HIERARCHY_VERSION_PROPERTY, version);
            log.info("Coverage hierarchy ingested from bundle version {}", version);
        } catch (Exception e) {
            log.error("Failed to ingest region hierarchy from {}: {}", tsvFilePath, e.getMessage(), e);
            recordIngestFailure(version, e);
        }
    }

    private void loadRegionHierarchy(Path tsvFilePath) throws IOException {
        createStagingTable();

        String copySql = """
                COPY coverage_region_ingest
                    (osm_id, parent_osm_id, kind, admin_level, resolution, total_cells, depth, child_count, continent)
                FROM STDIN WITH (FORMAT text, DELIMITER '\t', NULL '')
                """;

        transactionTemplate.executeWithoutResult(status -> {
            log.info("Streaming coverage hierarchy into staging table...");
            jdbcTemplate.execute("TRUNCATE TABLE " + STAGING_TABLE);
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

            log.info("Replacing coverage_region from staging table...");
            jdbcTemplate.update("TRUNCATE TABLE coverage_region");
            jdbcTemplate.update("""
                    INSERT INTO coverage_region
                        (osm_id, parent_osm_id, kind, admin_level, resolution, total_cells, depth, child_count, continent, name_key)
                    SELECT s.osm_id, s.parent_osm_id, s.kind, s.admin_level, s.resolution, s.total_cells, s.depth, s.child_count, s.continent, NULL
                    FROM %s s
                    """.formatted(STAGING_TABLE));

            StringBuilder continentCase = new StringBuilder("CASE continent");
            for (CoverageContinent continent : CoverageContinent.values()) {
                continentCase.append(" WHEN '").append(continent.code()).append("' THEN ").append(continent.id());
            }
            continentCase.append(" ELSE ").append(CoverageContinent.OTHER.id()).append(" END");
            jdbcTemplate.update("UPDATE coverage_region SET parent_osm_id = " + continentCase
                                        + " WHERE parent_osm_id IS NULL");
            jdbcTemplate.update("UPDATE coverage_region SET depth = depth + 1");

            Map<Long, Integer> childCounts = new HashMap<>();
            jdbcTemplate.query("SELECT parent_osm_id, COUNT(*) FROM coverage_region WHERE parent_osm_id < 0 GROUP BY parent_osm_id",
                               rs -> {
                                   childCounts.put(rs.getLong(1), rs.getInt(2));
                               });
            for (CoverageContinent continent : CoverageContinent.values()) {
                int childCount = childCounts.getOrDefault(continent.id(), 0);
                if (childCount == 0) {
                    continue;
                }
                jdbcTemplate.update("""
                                INSERT INTO coverage_region
                                    (osm_id, parent_osm_id, kind, admin_level, resolution, total_cells, depth, child_count, continent, name_key)
                                VALUES (?, NULL, 'CONTINENT', 0, 0, 0, 0, ?, ?, ?)
                                """,
                        continent.id(), childCount, continent.code(), continent.nameKey());
            }

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_coverage_region_parent ON coverage_region (parent_osm_id)");
        });

        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + STAGING_TABLE);
        } catch (Exception e) {
            log.debug("Could not clear staging table {}: {}", STAGING_TABLE, e.getMessage());
        }
    }

    private void createStagingTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS coverage_region_ingest (
                    osm_id bigint,
                    parent_osm_id bigint,
                    kind character varying(16),
                    admin_level integer,
                    resolution integer,
                    total_cells bigint,
                    depth integer,
                    child_count integer,
                    continent character varying(2)
                )
                """);
    }

    private void recordIngestFailure(String version, Exception e) {
        try {
            UUID jobId = UUID.randomUUID();
            Instant now = Instant.now();
            jobMetadataRepository.insert(jobId, "h3-hierarchy-ingest", JobType.H3_HIERARCHY_INGEST,
                    "Ingesting coverage hierarchy", JobState.FAILED, now, now, null);
            jobMetadataRepository.updateState(jobId, JobState.FAILED, now);
            jobMetadataRepository.updateProgress(jobId, 0, 1,
                    "Bundle " + version + ": " + e.getMessage());
        } catch (Exception metadataError) {
            log.warn("Could not record hierarchy ingest failure: {}", metadataError.getMessage());
        }
    }

    private long coverageRegionCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coverage_region WHERE kind = 'ADMIN'", Long.class);
        return count == null ? 0 : count;
    }

    private void loadOsmNames(Path tsvFilePath) {
        String createTableSql = """
                DROP TABLE IF EXISTS osm_names;
                CREATE TABLE osm_names (
                    osm_id bigint,
                    osm_type character(1),
                    all_names jsonb,
                    PRIMARY KEY (osm_id, osm_type)
                );
                """;

        String createBtreeIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_osm_names_id_type
                ON osm_names (osm_id, osm_type);
                """;

        String createGinIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_osm_names_all_names_gin
                ON osm_names USING gin (all_names);
                """;

        String copySql = "COPY osm_names (osm_id, osm_type, all_names) FROM STDIN WITH (FORMAT text, DELIMITER '\t', NULL '')";

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
