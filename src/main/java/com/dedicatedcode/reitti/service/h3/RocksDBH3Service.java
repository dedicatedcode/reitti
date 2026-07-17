package com.dedicatedcode.reitti.service.h3;

import com.uber.h3core.H3Core;
import jakarta.annotation.PreDestroy;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
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
public class RocksDBH3Service {

    static {
        RocksDB.loadLibrary(); // Crucial: Load native C++ bindings
    }

    private final H3Core h3;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Active Path
    private Path activeDbPath;

    // Active DB Connections
    private RocksDB h3ToOsmDb;
    private RocksDB regionMetadataDb;
    private RocksDB regionGeometryDb;

    // Active native options (must be kept to close them later)
    private Options h3ToOsmDbOptions;
    private Options regionMetadataDbOptions;
    private Options regionGeometryDbOptions;

    public RocksDBH3Service() throws IOException {
        this.h3 = H3Core.newInstance(); // Thread-safe native H3 engine
    }

    // ==========================================
    // 1. Core Lookup & Geometry Query Methods
    // ==========================================

    /**
     * Looks up the administrative boundaries for a given coordinate.
     * Returns a list of boundaries (OSM ID and total cell count) that contain this point.
     * Uses the same resolution logic as StandaloneBoundaryImporter.
     */
    public List<BoundaryInfo> lookup(double lat, double lng) {
        rwLock.readLock().lock();
        try {
            // Fail-fast if the DBs aren't loaded yet
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

    /**
     * Fetches the H3 cells for a specific lat,lon in all resolutions used by the importer.
     * Uses the same resolution logic as StandaloneBoundaryImporter.
     */
    public Set<Long> getCellsForPoint(double lat, double lng) {
        Set<Long> cells = new HashSet<>();

        // Use the same resolutions as the importer (4, 6, 9)
        int[] resolutions = {4, 6, 9};

        for (int resolution : resolutions) {
            cells.add(h3.latLngToCell(lat, lng, resolution));
        }

        return cells;
    }


    /**
     * Returns H3 cells for a point along with their associated OSM boundary IDs.
     * Useful for Reitti to track which boundaries are associated with each visited cell.
     */
    public List<CellWithBoundaries> getCellsWithBoundaries(double lat, double lng) {
        rwLock.readLock().lock();
        try {
            // Fail-fast if the DBs aren't loaded yet
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

    // ==========================================
    // 2. Private Binary Decoding Helpers
    // ==========================================

    /**
     * Decodes a list of Long OSM IDs from the h3ToOsmDb.
     * Assumes key is 8-byte H3 index, value is contiguous 8-byte blocks of Long OSM IDs.
     */
    private Set<Long> getOsmIdsForCell(long cellId) {
        byte[] key = ByteBuffer.allocate(8).putLong(cellId).array();
        try {
            byte[] value = h3ToOsmDb.get(key);
            if (value == null || value.length == 0) {
                return Set.of();
            }

            // Fast decode: Each Long takes 8 bytes
            int count = value.length / 8;
            Set<Long> osmIds = new HashSet<>(count);
            ByteBuffer buffer = ByteBuffer.wrap(value);
            for (int i = 0; i < count; i++) {
                osmIds.add(buffer.getLong());
            }
            return osmIds;
        } catch (RocksDBException e) {
            System.err.println("Failed to lookup OSM IDs for cell " + cellId + ": " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Decodes total cells from the regionMetadataDb.
     * Assumes key is 8-byte OSM ID, value is 4-byte or 8-byte representation of total cells.
     */
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
                return (int) buffer.getLong(); // Safely downcast if stored as Long
            }
            return 0;
        } catch (RocksDBException e) {
            System.err.println("Failed to lookup total cells for OSM ID " + osmId + ": " + e.getMessage());
            return 0;
        }
    }

    // ==========================================
    // 3. Transactional Hot-Swap Logic
    // ==========================================

    /**
     * Atomically swaps all three database instances with zero downtime or memory leaks.
     */
    public void hotSwapDatabase(Path newDbPath) throws RocksDBException {
        System.out.println("Initiating multi-db hot-swap to: " + newDbPath);

        // Define expected subfolders inside the extracted directory
        Path h3ToOsmPath = newDbPath.resolve("h3_to_osm");
        Path metadataPath = newDbPath.resolve("region_metadata");
        Path geometryPath = newDbPath.resolve("region_geometry");

        // Validate folder health before trying to touch JNI
        if (!Files.isDirectory(h3ToOsmPath) || !Files.isDirectory(metadataPath) || !Files.isDirectory(geometryPath)) {
            throw new IllegalArgumentException("Target directory is missing subfolders (h3_to_osm, region_metadata, region_geometry)");
        }

        // 1. Instantiate three separate Options objects
        Options newH3ToOsmOptions = new Options().setCreateIfMissing(false);
        Options newMetadataOptions = new Options().setCreateIfMissing(false);
        Options newGeometryOptions = new Options().setCreateIfMissing(false);

        RocksDB newH3ToOsmDb = null;
        RocksDB newMetadataDb = null;
        RocksDB newGeometryDb;

        try {
            // 2. Open all three new databases (This is heavy and takes time, done OUTSIDE of the lock)
            newH3ToOsmDb = RocksDB.open(newH3ToOsmOptions, h3ToOsmPath.toAbsolutePath().toString());
            newMetadataDb = RocksDB.open(newMetadataOptions, metadataPath.toAbsolutePath().toString());
            newGeometryDb = RocksDB.open(newGeometryOptions, geometryPath.toAbsolutePath().toString());
        } catch (RocksDBException e) {
            // Transaction failed! Clean up opened instances immediately to prevent dangling native files
            if (newH3ToOsmDb != null) newH3ToOsmDb.close();
            if (newMetadataDb != null) newMetadataDb.close();

            newH3ToOsmOptions.close();
            newMetadataOptions.close();
            newGeometryOptions.close();
            throw e; // Bubble up exception to skip local-manifest update
        }

        // 3. TRANSACTION COMDEMNED SUCCESSFUL: Enter exclusive Write-Lock block to perform atomic swap
        rwLock.writeLock().lock();

        // Save pointers of old databases so we can dispose of them safely outside the lock
        RocksDB oldH3ToOsmDb = this.h3ToOsmDb;
        RocksDB oldMetadataDb = this.regionMetadataDb;
        RocksDB oldGeometryDb = this.regionGeometryDb;

        Options oldH3ToOsmOptions = this.h3ToOsmDbOptions;
        Options oldMetadataOptions = this.regionMetadataDbOptions;
        Options oldGeometryOptions = this.regionGeometryDbOptions;

        Path oldPath = this.activeDbPath;

        try {
            // Atomic re-routing of pointers
            this.h3ToOsmDb = newH3ToOsmDb;
            this.regionMetadataDb = newMetadataDb;
            this.regionGeometryDb = newGeometryDb;

            this.h3ToOsmDbOptions = newH3ToOsmOptions;
            this.regionMetadataDbOptions = newMetadataOptions;
            this.regionGeometryDbOptions = newGeometryOptions;

            this.activeDbPath = newDbPath;

            System.out.println("Pointer swap completed successfully.");
        } finally {
            rwLock.writeLock().unlock(); // Instantly unblock reading API
        }

        // 4. Safely close old resources outside the write lock
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
                System.err.println("Failed to cleanly close JNI DB pointer: " + e.getMessage());
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
                System.out.println("Deleting old version directory: " + pathToDelete);
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
                System.err.println("Failed to delete older DB folder: " + e.getMessage());
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

    /**
     * Represents a boundary containing the queried point.
     * Reitti can use the totalCells to calculate the percentage visited.
     */
    public record BoundaryInfo(long osmId, int totalCells) {
    }

    /**
     * Represents an H3 cell with its associated boundary OSM IDs.
     * Useful for tracking which boundaries are affected when a cell is visited.
     */
    public record CellWithBoundaries(long cellId, int resolution, Set<Long> osmIds) {
    }
}