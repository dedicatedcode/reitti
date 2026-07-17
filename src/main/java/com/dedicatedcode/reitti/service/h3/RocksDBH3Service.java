package com.dedicatedcode.reitti.service.h3;

import com.uber.h3core.H3Core;
import jakarta.annotation.PreDestroy;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@ConditionalOnProperty(prefix = "reitti.h3", name = "enabled", havingValue = "true")
public class RocksDBH3Service {

    private static final Logger log = LoggerFactory.getLogger(RocksDBH3Service.class);

    static {
        RocksDB.loadLibrary();
    }

    private final H3Core h3;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private Path activeDbPath;
    private RocksDB h3ToOsmDb;
    private RocksDB regionMetadataDb;
    private RocksDB regionGeometryDb;

    private Options h3ToOsmDbOptions;
    private Options regionMetadataDbOptions;
    private Options regionGeometryDbOptions;

    public RocksDBH3Service() throws IOException {
        this.h3 = H3Core.newInstance();
    }

    public List<BoundaryInfo> lookup(double lat, double lng) {
        rwLock.readLock().lock();
        try {
            if (h3ToOsmDb == null || regionMetadataDb == null) {
                throw new IllegalStateException("H3 Database is currently offline or updating.");
            }

            Set<Long> osmIds = new HashSet<>();
            int[] resolutions = {4, 6, 9};

            for (int resolution : resolutions) {
                long cellId = h3.latLngToCell(lat, lng, resolution);
                osmIds.addAll(getOsmIdsForCell(cellId));
            }

            List<BoundaryInfo> results = new ArrayList<>();
            for (Long osmId : osmIds) {
                int totalCells = getTotalCells(osmId);
                results.add(new BoundaryInfo(osmId, totalCells));
            }

            return results;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Set<Long> getCellsForPoint(double lat, double lng) {
        Set<Long> cells = new HashSet<>();
        int[] resolutions = {4, 6, 9};

        for (int resolution : resolutions) {
            cells.add(h3.latLngToCell(lat, lng, resolution));
        }

        return cells;
    }

    public Long getLevel9CellForPoint(double lat, double lng) {
        return h3.latLngToCell(lat, lng, 9);
    }

    public List<CellWithBoundaries> getCellsWithBoundaries(double lat, double lng) {
        rwLock.readLock().lock();
        try {
            if (h3ToOsmDb == null || regionMetadataDb == null) {
                throw new IllegalStateException("H3 Database is currently offline or updating.");
            }
            List<CellWithBoundaries> result = new ArrayList<>();

            int[] resolutions = {4, 6, 9};

            for (int resolution : resolutions) {
                long cellId = h3.latLngToCell(lat, lng, resolution);
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

    private Set<Long> getOsmIdsForCell(long cellId) {
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
        }
    }

    private int getTotalCells(long osmId) {
        byte[] key = ByteBuffer.allocate(8).putLong(osmId).array();
        try {
            byte[] value = regionMetadataDb.get(key);
            if (value == null || value.length == 0) {
                return 0;
            }

            ByteBuffer buffer = ByteBuffer.wrap(value);
            if (value.length == 4) {
                return buffer.getInt();
            } else if (value.length == 8) {
                return (int) buffer.getLong();
            }
            return 0;
        } catch (RocksDBException e) {
            log.error("Failed to lookup total cells for OSM ID {}: {}", osmId, e.getMessage());
            return 0;
        }
    }

    public void hotSwapDatabase(Path newDbPath) throws RocksDBException {
        log.info("Initiating multi-db hot-swap to: {}", newDbPath);

        Path h3ToOsmPath = newDbPath.resolve("h3_to_osm");
        Path metadataPath = newDbPath.resolve("region_metadata");
        Path geometryPath = newDbPath.resolve("region_geometry");

        if (!Files.isDirectory(h3ToOsmPath) || !Files.isDirectory(metadataPath) || !Files.isDirectory(geometryPath)) {
            throw new IllegalArgumentException("Target directory is missing subfolders (h3_to_osm, region_metadata, region_geometry)");
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
        try {
            closeDbResources(h3ToOsmDb, h3ToOsmDbOptions);
            closeDbResources(regionMetadataDb, regionMetadataDbOptions);
            closeDbResources(regionGeometryDb, regionGeometryDbOptions);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public record BoundaryInfo(long osmId, int totalCells) {
    }

    public record CellWithBoundaries(long cellId, int resolution, Set<Long> osmIds) {
    }
}
