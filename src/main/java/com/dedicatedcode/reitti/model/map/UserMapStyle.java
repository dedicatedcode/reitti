package com.dedicatedcode.reitti.model.map;

public record UserMapStyle(
        Long id,
        Long userId,
        String name,
        String mapType,
        String styleInputType,
        String rasterSourceInputType,
        String styleJson,
        String styleUrl,
        MapStyleDataSource dataSource,
        MapStyleVectorOptions vectorOptions,
        boolean shared,
        Long version
) {
    public String frontendId() {
        return "custom-" + id;
    }

    public String styleInput() {
        return styleJson != null ? styleJson : styleUrl;
    }
}
