package com.dedicatedcode.reitti.model.processing;

import java.io.Serializable;
import java.time.Instant;

public class DetectionParameter implements Serializable {
    private final Long id;
    private final VisitDetection visitDetection;
    private final VisitMerging visitMerging;
    private final Instant validSince;
    private final boolean needsRecalculation;

    public DetectionParameter(Long id, VisitDetection visitDetection, VisitMerging visitMerging, Instant validSince, boolean needsRecalculation) {
        this.id = id;
        this.visitDetection = visitDetection;
        this.visitMerging = visitMerging;
        this.validSince = validSince;
        this.needsRecalculation = needsRecalculation;
    }

    public Long getId() {
        return id;
    }

    public VisitDetection getVisitDetection() {
        return visitDetection;
    }

    public VisitMerging getVisitMerging() {
        return visitMerging;
    }

    public Instant getValidSince() {
        return validSince;
    }

    public boolean needsRecalculation() {
        return this.needsRecalculation;
    }

    public DetectionParameter withNeedsRecalculation(boolean needsRecalculation) {
        return new DetectionParameter(this.id, this.visitDetection, this.visitMerging, this.validSince, needsRecalculation);
    }

    public static class VisitDetection implements Serializable{
        private final long searchDistanceInMeters;
        private final int minimumAdjacentPoints;
        private final long minimumStayTimeInSeconds;
        private final long maxMergeTimeBetweenSameStayPoints;

        public VisitDetection(long searchDistanceInMeters, int minimumAdjacentPoints,
                             long minimumStayTimeInSeconds, long maxMergeTimeBetweenSameStayPoints) {
            this.searchDistanceInMeters = searchDistanceInMeters;
            this.minimumAdjacentPoints = minimumAdjacentPoints;
            this.minimumStayTimeInSeconds = minimumStayTimeInSeconds;
            this.maxMergeTimeBetweenSameStayPoints = maxMergeTimeBetweenSameStayPoints;
        }

        public long getSearchDistanceInMeters() {
            return searchDistanceInMeters;
        }

        public int getMinimumAdjacentPoints() {
            return minimumAdjacentPoints;
        }

        public long getMinimumStayTimeInSeconds() {
            return minimumStayTimeInSeconds;
        }

        public long getMaxMergeTimeBetweenSameStayPoints() {
            return maxMergeTimeBetweenSameStayPoints;
        }
    }

    public static class VisitMerging implements Serializable {
        private final long searchDurationInHours;
        private final long maxMergeTimeBetweenSameVisits;
        private final long minDistanceBetweenVisits;

        public VisitMerging(long searchDurationInHours, long maxMergeTimeBetweenSameVisits, 
                           long minDistanceBetweenVisits) {
            this.searchDurationInHours = searchDurationInHours;
            this.maxMergeTimeBetweenSameVisits = maxMergeTimeBetweenSameVisits;
            this.minDistanceBetweenVisits = minDistanceBetweenVisits;
        }

        public long getSearchDurationInHours() {
            return searchDurationInHours;
        }

        public long getMaxMergeTimeBetweenSameVisits() {
            return maxMergeTimeBetweenSameVisits;
        }

        public long getMinDistanceBetweenVisits() {
            return minDistanceBetweenVisits;
        }
    }
}
