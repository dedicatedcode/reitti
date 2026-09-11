package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
/**
 * A read-only view over the points of a time range that knows its count and
 * time bounds upfront (via an aggregate query) and streams the points
 * themselves in bounded chunks using a keyset cursor on (timestamp, id), so
 * arbitrarily large ranges can be traversed with bounded memory.
 * <p>
 * Each call to {@link #iterator()} returns a fresh cursor over the same range,
 * so the stream can be iterated multiple times.
 */
public class RawLocationPointStream implements Iterable<RawLocationPoint> {

    public record Stats(long count, Instant firstTimestamp, Instant lastTimestamp) {
    }

    @FunctionalInterface
    public interface ChunkFetcher {
        List<RawLocationPoint> fetch(Instant afterTimestamp, Long afterId, int limit);
    }

    private final Stats stats;
    private final ChunkFetcher fetcher;
    private final int chunkSize;

    public RawLocationPointStream(Stats stats, ChunkFetcher fetcher, int chunkSize) {
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
        return new KeysetIterator();
    }

    /**
     * Lazily loads points in chunks using a keyset cursor on (timestamp, id),
     * so arbitrarily large time ranges can be traversed with bounded memory.
     */
    private class KeysetIterator implements Iterator<RawLocationPoint> {

        private Iterator<RawLocationPoint> currentChunk = List.<RawLocationPoint>of().iterator();
        private Instant afterTimestamp;
        private Long afterId;
        private boolean exhausted;

        @Override
        public boolean hasNext() {
            while (!currentChunk.hasNext() && !exhausted) {
                List<RawLocationPoint> chunk = fetcher.fetch(afterTimestamp, afterId, chunkSize);
                if (chunk.isEmpty()) {
                    exhausted = true;
                } else {
                    RawLocationPoint last = chunk.getLast();
                    afterTimestamp = last.getTimestamp();
                    afterId = last.getId();
                    if (chunk.size() < chunkSize) {
                        exhausted = true;
                    }
                }
                currentChunk = chunk.iterator();
            }
            return currentChunk.hasNext();
        }

        @Override
        public RawLocationPoint next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentChunk.next();
        }
    }
}
