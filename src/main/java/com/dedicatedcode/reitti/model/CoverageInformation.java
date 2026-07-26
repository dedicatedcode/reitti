package com.dedicatedcode.reitti.model;

import java.util.List;

public record CoverageInformation(
        long osmId,
        String name,
        int totalCells,
        int visitedCells,
        double coveragePercentage,
        List<Long> visitedCellIds) {
}
