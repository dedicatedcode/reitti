package com.dedicatedcode.reitti.service;

import java.util.List;

public interface SpatialCoverageService {

    Long getLevelCellForPoint(double latitude, double longitude, int resolution);

    void postPromotion(List<Long> insertedIds);
}
