package com.dedicatedcode.reitti.model.processing;

public record Configuration() {
    public record VisitDetection(int searchDistanceInMeters, int minimumClosePoints, int minimumStayTimeInSeconds, int maxTimeBetweenSameStayPoints) {}
    public record VisitMerging(int searchDurationInHours, int maxTimeBetweenSameVisits, int mergeThresholdMeters) {}
}
