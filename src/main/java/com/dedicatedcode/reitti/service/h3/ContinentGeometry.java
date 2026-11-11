package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.CoverageContinent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static continent outlines shipped with Reitti. They are display-only: continent membership is
 * decided by the {@code continent} tag of the area, this geometry is only used for previews.
 */
public final class ContinentGeometry {

    private static final Logger log = LoggerFactory.getLogger(ContinentGeometry.class);
    private static final Map<CoverageContinent, byte[]> CACHE = new ConcurrentHashMap<>();

    private ContinentGeometry() {
    }

    public static byte[] wkb(long osmId) {
        CoverageContinent continent = CoverageContinent.byId(osmId);
        return continent == null ? null : CACHE.computeIfAbsent(continent, ContinentGeometry::load);
    }

    private static byte[] load(CoverageContinent continent) {
        String resource = "/coverage/continents/" + continent.code() + ".wkb";
        try (InputStream stream = ContinentGeometry.class.getResourceAsStream(resource)) {
            if (stream == null) {
                log.warn("Continent geometry {} is missing", resource);
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            log.warn("Could not load continent geometry {}: {}", resource, e.getMessage());
            return null;
        }
    }
}
