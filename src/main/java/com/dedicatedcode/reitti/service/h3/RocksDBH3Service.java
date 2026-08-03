package com.dedicatedcode.reitti.service.h3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.h3core.H3Core;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class RocksDBH3Service {

    private static final Logger log = LoggerFactory.getLogger(RocksDBH3Service.class);

    static {
        RocksDB.loadLibrary();
    }
    static final List<Integer> SUPPORTED_RESOLUTIONS = List.of(4, 6, 9);

    private final H3Core h3;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private Path activeDbPath;
    private RocksDB h3ToOsmDb;
    private RocksDB regionMetadataDb;
    private RocksDB regionGeometryDb;

    private Options h3ToOsmDbOptions;
    private Options regionMetadataDbOptions;
    private Options regionGeometryDbOptions;

    private final Path rootDbDir;
    private final Path localManifestPath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RocksDBH3Service(@Value("${reitti.h3.root-dir}") String h3RootDir) throws IOException {
        this.h3 = H3Core.newInstance();
        this.rootDbDir = Path.of(h3RootDir);
        this.localManifestPath = rootDbDir.resolve("local-manifest.json");
    }

    @PostConstruct
    public void tryLoadLocalDatabase() {
        try {
            Files.createDirectories(rootDbDir);

            if (!Files.exists(localManifestPath)) {
                log.info("No local manifest found at {}. Skipping local DB load.", localManifestPath);
                return;
            }

            H3Manifest localManifest = objectMapper.readValue(localManifestPath.toFile(), H3Manifest.class);
            String version = localManifest.getVersion();
            Path targetVersionDir = rootDbDir.resolve("version_" + version);

            if (!Files.isDirectory(targetVersionDir)) {
                log.info("Local manifest references version '{}' but directory {} does not exist. Skipping local DB load.", version, targetVersionDir);
                return;
            }

            log.info("Found local manifest for version '{}'. Loading database from {}...", version, targetVersionDir);
            hotSwapDatabase(targetVersionDir);
            log.info("Successfully loaded local H3 database version '{}'.", version);
        } catch (Exception e) {
            log.warn("Failed to load local H3 database from manifest: {}. Will rely on lifecycle manager.", e.getMessage());
        }
    }

    public Set<Long> getParentCells(long h3Cell) {
        return SUPPORTED_RESOLUTIONS.stream().map(r -> h3.cellToParent(h3Cell,r)).collect(Collectors.toSet());
    }

    public Set<Long> getCellsForPoint(double lat, double lng) {
        Set<Long> cells = new HashSet<>();
        int[] resolutions = {4, 6, 9};

        for (int resolution : resolutions) {
            cells.add(h3.latLngToCell(lat, lng, resolution));
        }

        return cells;
    }

    public List<CellWithBoundaries> getCellsWithBoundaries(long h3Cell) {
        rwLock.readLock().lock();
        try {
            if (h3ToOsmDb == null || regionMetadataDb == null) {
                throw new IllegalStateException("H3 Database is currently offline or updating.");
            }
            List<CellWithBoundaries> result = new ArrayList<>();

            int cellResolution = h3.getResolution(h3Cell);
            for (int resolution : SUPPORTED_RESOLUTIONS) {
                if (resolution > cellResolution) {
                    continue;
                }
                long cellId = h3.cellToParent(h3Cell, resolution);
                Set<Long> osmIds = getOsmIdsForCell(cellId);
                if (!osmIds.isEmpty()) {
                    result.add(new CellWithBoundaries(cellId, resolution, osmIds));
                }
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isAvailable() {
        return h3ToOsmDb != null && regionMetadataDb != null;
    }

    public int getResolution(long h3Cell) {
        return h3.getResolution(h3Cell);
    }

    public Set<Long> getOsmIds(long cellId) {
        return getOsmIdsForCell(cellId);
    }

    private Set<Long> getOsmIdsForCell(long cellId) {
        rwLock.readLock().lock();
        byte[] key = ByteBuffer.allocate(8).putLong(cellId).array();
        try {
            byte[] value = h3ToOsmDb.get(key);
            if (value == null || value.length == 0) {
                return Set.of();
            }

            int count = value.length / 8;
            Set<Long> osmIds = new HashSet<>(count);
            ByteBuffer buffer = ByteBuffer.wrap(value);
            for (int i = 0; i < count; i++) {
                osmIds.add(buffer.getLong());
            }
            return osmIds;
        } catch (RocksDBException e) {
            log.error("Failed to lookup OSM IDs for cell {}: {}", cellId, e.getMessage());
            return Set.of();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getTotalCells(long osmId, int targetResolution) {
        rwLock.readLock().lock();
        byte[] key = ByteBuffer.allocate(8).putLong(osmId).array();
        try {
            byte[] value = regionMetadataDb.get(key);
            if (value == null || value.length == 0) {
                return 0;
            }

            ByteBuffer buffer = ByteBuffer.wrap(value).order(java.nio.ByteOrder.BIG_ENDIAN);
            if (value.length != 12) {
                log.warn("Unexpected region_metadata value length {} for OSM ID {}", value.length, osmId);
                return 0;
            }

            long cellCount = buffer.getLong();
            int storedResolution = buffer.getInt();
            int storedTotal = cellCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cellCount;

            return scaleTotalCells(storedTotal, storedResolution, targetResolution);
        } catch (RocksDBException e) {
            log.error("Failed to lookup total cells for OSM ID {}: {}", osmId, e.getMessage());
            return 0;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private int scaleTotalCells(int storedTotal, int storedResolution, int targetResolution) {
        if (targetResolution == storedResolution || storedTotal <= 0) {
            return storedTotal;
        }
        if (targetResolution > storedResolution) {
            return (int) Math.min((long) storedTotal * (long) Math.pow(7, targetResolution - storedResolution), Integer.MAX_VALUE);
        }
        return Math.max(1, storedTotal / (int) Math.pow(7, storedResolution - targetResolution));
    }

    public void hotSwapDatabase(Path newDbPath) throws RocksDBException {
        log.info("Initiating multi-db hot-swap to: {}", newDbPath);

        Path h3ToOsmPath = newDbPath.resolve("h3_to_osm");
        Path metadataPath = newDbPath.resolve("region_metadata");
        Path geometryPath = newDbPath.resolve("region_geometry");

        if (!Files.isDirectory(h3ToOsmPath) || !Files.isDirectory(metadataPath) || !Files.isDirectory(geometryPath)) {
            throw new IllegalArgumentException("Target directory is missing subfolders (h3_to_osm, region_metadata, region_geometry)");
        }

        if (newDbPath.equals(this.activeDbPath)) {
            log.info("Database [{}] is already active. Skipping hot-swap.", newDbPath);
            return;
        }

        Options newH3ToOsmOptions = new Options().setCreateIfMissing(false);
        Options newMetadataOptions = new Options().setCreateIfMissing(false);
        Options newGeometryOptions = new Options().setCreateIfMissing(false);

        RocksDB newH3ToOsmDb = null;
        RocksDB newMetadataDb = null;
        RocksDB newGeometryDb;

        try {
            newH3ToOsmDb = RocksDB.open(newH3ToOsmOptions, h3ToOsmPath.toAbsolutePath().toString());
            newMetadataDb = RocksDB.open(newMetadataOptions, metadataPath.toAbsolutePath().toString());
            newGeometryDb = RocksDB.open(newGeometryOptions, geometryPath.toAbsolutePath().toString());
        } catch (RocksDBException e) {
            if (newH3ToOsmDb != null) newH3ToOsmDb.close();
            if (newMetadataDb != null) newMetadataDb.close();

            newH3ToOsmOptions.close();
            newMetadataOptions.close();
            newGeometryOptions.close();
            throw e;
        }

        rwLock.writeLock().lock();

        RocksDB oldH3ToOsmDb = this.h3ToOsmDb;
        RocksDB oldMetadataDb = this.regionMetadataDb;
        RocksDB oldGeometryDb = this.regionGeometryDb;

        Options oldH3ToOsmOptions = this.h3ToOsmDbOptions;
        Options oldMetadataOptions = this.regionMetadataDbOptions;
        Options oldGeometryOptions = this.regionGeometryDbOptions;

        Path oldPath = this.activeDbPath;

        try {
            this.h3ToOsmDb = newH3ToOsmDb;
            this.regionMetadataDb = newMetadataDb;
            this.regionGeometryDb = newGeometryDb;

            this.h3ToOsmDbOptions = newH3ToOsmOptions;
            this.regionMetadataDbOptions = newMetadataOptions;
            this.regionGeometryDbOptions = newGeometryOptions;

            this.activeDbPath = newDbPath;

            log.info("Pointer swap completed successfully.");
        } finally {
            rwLock.writeLock().unlock();
        }

        closeDbResources(oldH3ToOsmDb, oldH3ToOsmOptions);
        closeDbResources(oldMetadataDb, oldMetadataOptions);
        closeDbResources(oldGeometryDb, oldGeometryOptions);

        if (oldPath != null) {
            cleanupOldDatabaseDirectoryAsync(oldPath);
        }
    }

    private void closeDbResources(RocksDB db, Options options) {
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                log.error("Failed to cleanly close JNI DB pointer: {}", e.getMessage());
            }
        }
        if (options != null) {
            options.close();
        }
    }

    private void cleanupOldDatabaseDirectoryAsync(Path pathToDelete) {
        if (pathToDelete == null || !Files.exists(pathToDelete)) return;

        Thread.ofVirtual().start(() -> {
            try {
                log.info("Deleting old version directory: {}", pathToDelete);
                try (var stream = Files.walk(pathToDelete)) {
                    stream.sorted((p1, p2) -> p2.compareTo(p1))
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            } catch (Exception e) {
                log.error("Failed to delete older DB folder: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        rwLock.writeLock().lock();
        log.info("Shutting down RocksDBH3Service");
        try {
            closeDbResources(h3ToOsmDb, h3ToOsmDbOptions);
            closeDbResources(regionMetadataDb, regionMetadataDbOptions);
            closeDbResources(regionGeometryDb, regionGeometryDbOptions);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void deleteOldVersions() {
        if (activeDbPath == null) {
            return;
        }

        Thread.ofVirtual().start(() -> {
            try {
                log.info("Cleaning up old H3 database versions...");
                try (Stream<Path> entries = Files.list(rootDbDir)) {
                    entries.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("version_"))
                            .filter(p -> !p.equals(activeDbPath))
                            .forEach(p -> {
                                try {
                                    log.info("Deleting old database version: {}", p.getFileName());
                                    try (Stream<Path> walk = Files.walk(p)) {
                                        walk.sorted(Comparator.reverseOrder())
                                                .forEach(f -> {
                                                    try {
                                                        Files.delete(f);
                                                    } catch (IOException ignored) {
                                                    }
                                                });
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to delete old version directory {}: {}", p, e.getMessage());
                                }
                            });
                }
                log.info("Old H3 database versions cleaned up.");
            } catch (Exception e) {
                log.error("Failed to clean up old H3 database versions: {}", e.getMessage());
            }
        });
    }

    public record CellWithBoundaries(long cellId, int resolution, Set<Long> osmIds) {
    }
}
