package com.dedicatedcode.reitti.model;

public record CoverageRegion(
        long osmId,
        Long parentOsmId,
        RegionKind kind,
        int adminLevel,
        int resolution,
        long totalCells,
        int depth,
        int childCount,
        String continent,
        String nameKey) {

    public boolean isContinent() {
        return kind == RegionKind.CONTINENT;
    }

    public enum RegionKind {
        CONTINENT,
        ADMIN
    }
}
