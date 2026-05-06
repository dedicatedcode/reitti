package com.dedicatedcode.reitti.dto.map;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;

public record SaveMapStyleRequest(
        String id,
        String label,
        String mapType,
        String styleInputType,
        String rasterSourceInputType,
        String styleInput,
        boolean shared,
        MapStyleDataSource dataSource,
        MapStyleVectorOptions vectorOptions
) {
}
