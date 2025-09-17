package com.dedicatedcode.reitti.model.processing;

import java.time.Instant;

public record Configuration(
    VisitDetection visitDetection,
    VisitMerging visitMerging,
    Instant validSince
) {
    public record VisitDetection(
        long searchDistanceInMeters, 
        long minimumAdjacentPoints, 
        long minimumStayTimeInSeconds, 
        long maxMergeTimeBetweenSameStayPoints
    ) {}
    
    public record VisitMerging(
        long searchDurationInHours, 
        long maxMergeTimeBetweenSameVisits, 
        long minDistanceBetweenVisits
    ) {}
}
