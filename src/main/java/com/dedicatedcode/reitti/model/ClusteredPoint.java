package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;

public class ClusteredPoint {
    private final RawLocationPoint point;
    private final Integer clusterId;

    public ClusteredPoint(RawLocationPoint point, Integer clusterId) {
        this.point = point;
        this.clusterId = clusterId;
    }

    public RawLocationPoint getPoint() {
        return point;
    }

    public Integer getClusterId() {
        return clusterId;
    }
}
