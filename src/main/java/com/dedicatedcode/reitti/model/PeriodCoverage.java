package com.dedicatedcode.reitti.model;

import java.io.Serializable;

/**
 * Exploration numbers of a time range, based on H3 cells at resolution 9.
 *
 * @param homeAreaName       name of the administrative area containing the home, null if unknown
 * @param homeAreaBefore     share of the home area's cells visited before the range
 * @param homeAreaAfter      share of the home area's cells visited until the end of the range
 * @param newCells           cells visited for the first time inside the range
 * @param totalCells         cells visited until the end of the range
 * @param knownDistanceShare share of the travelled distance on cells known before the range, null if not calculated
 * @param newDistanceMeters  distance travelled on cells that were new in the range
 * @param direction          dominant compass direction (n, ne, e, se, s, sw, w, nw) of new cells seen from home, or null
 */
public record PeriodCoverage(String homeAreaName,
                             Double homeAreaBefore,
                             Double homeAreaAfter,
                             int newCells,
                             int totalCells,
                             Double knownDistanceShare,
                             double newDistanceMeters,
                             String direction) implements Serializable {
}
