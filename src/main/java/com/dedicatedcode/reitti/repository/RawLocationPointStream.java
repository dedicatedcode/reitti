package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;

import java.time.Instant;
import java.util.Iterator;

/**
 * A single-use view over the points of a time range that knows its count and
 * time bounds upfront (via an aggregate query) and streams the points
 * themselves in bounded chunks. Each call to {@link #iterator()} returns a
 * fresh cursor over the same range.
 */
public class RawLocationPointStream implements Iterable<RawLocationPoint> {

    public record Stats(long count, Instant firstTimestamp, Instant lastTimestamp) {
    }

    private final Stats stats;
    private final KeysetPointIterator.ChunkFetcher fetcher;
    private final int chunkSize;

    public RawLocationPointStream(Stats stats, KeysetPointIterator.ChunkFetcher fetcher, int chunkSize) {
        this.stats = stats;
        this.fetcher = fetcher;
        this.chunkSize = chunkSize;
    }

    public long getCount() {
        return stats.count();
    }

    public Instant getFirstTimestamp() {
        return stats.firstTimestamp();
    }

    public Instant getLastTimestamp() {
        return stats.lastTimestamp();
    }

    @Override
    public Iterator<RawLocationPoint> iterator() {
        return new KeysetPointIterator(fetcher, chunkSize);
    }
}
