package com.dedicatedcode.reitti.dto.map;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;

public record MapStyleConfigDTO(
        String id,
        String label,
        String mapType,
        String styleInputType,
        String rasterSourceInputType,
        String styleUrl,
        String styleInput,
        boolean custom,
        boolean shared,
        boolean editable,
        MapStyleDataSource dataSource,
        MapStyleVectorOptions vectorOptions
) {
}
