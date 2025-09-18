package com.dedicatedcode.reitti.model.processing;

import java.time.Instant;

public class Configuration {
    private final Long id;
    private final VisitDetection visitDetection;
    private final VisitMerging visitMerging;
    private final Instant validSince;

    public Configuration(Long id, VisitDetection visitDetection, VisitMerging visitMerging, Instant validSince) {
        this.id = id;
        this.visitDetection = visitDetection;
        this.visitMerging = visitMerging;
        this.validSince = validSince;
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

    public static class VisitDetection {
        private final long searchDistanceInMeters;
        private final long minimumAdjacentPoints;
        private final long minimumStayTimeInSeconds;
        private final long maxMergeTimeBetweenSameStayPoints;

        public VisitDetection(long searchDistanceInMeters, long minimumAdjacentPoints, 
                             long minimumStayTimeInSeconds, long maxMergeTimeBetweenSameStayPoints) {
            this.searchDistanceInMeters = searchDistanceInMeters;
            this.minimumAdjacentPoints = minimumAdjacentPoints;
            this.minimumStayTimeInSeconds = minimumStayTimeInSeconds;
            this.maxMergeTimeBetweenSameStayPoints = maxMergeTimeBetweenSameStayPoints;
        }

        public long getSearchDistanceInMeters() {
            return searchDistanceInMeters;
        }

        public long getMinimumAdjacentPoints() {
            return minimumAdjacentPoints;
        }

        public long getMinimumStayTimeInSeconds() {
            return minimumStayTimeInSeconds;
        }

        public long getMaxMergeTimeBetweenSameStayPoints() {
            return maxMergeTimeBetweenSameStayPoints;
        }
    }

    public static class VisitMerging {
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
