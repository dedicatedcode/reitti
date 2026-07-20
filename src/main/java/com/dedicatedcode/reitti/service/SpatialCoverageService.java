package com.dedicatedcode.reitti.service;

public interface SpatialCoverageService {

    Long getLevelCellForPoint(double latitude, double longitude, int resolution);
}
