package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lazily loads points in chunks using a keyset cursor on (timestamp, id),
 * so arbitrarily large time ranges can be traversed with bounded memory.
 */
public class KeysetPointIterator implements Iterator<RawLocationPoint> {

    public interface ChunkFetcher {
        List<RawLocationPoint> fetch(Instant afterTimestamp, Long afterId, int limit);
    }

    private final ChunkFetcher fetcher;
    private final int chunkSize;

    private Iterator<RawLocationPoint> currentChunk = Collections.emptyIterator();
    private Instant afterTimestamp;
    private Long afterId;
    private boolean exhausted;

    public KeysetPointIterator(ChunkFetcher fetcher, int chunkSize) {
        this.fetcher = fetcher;
        this.chunkSize = chunkSize;
    }

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
