package com.dedicatedcode.reitti.model.map;

import com.dedicatedcode.reitti.dto.map.MapStyleConfigDTO;
import com.dedicatedcode.reitti.model.security.User;

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
        boolean defaultStyle,
        boolean shared,
        Long version
) {
    public String styleInput() {
        return styleJson != null ? styleJson : styleUrl;
    }

    public MapStyleConfigDTO toDto(User user) {
        return new MapStyleConfigDTO(
                id,
                name(),
                mapType(),
                styleInputType(),
                rasterSourceInputType(),
                styleUrl,
                styleInput(),
                !defaultStyle,
                shared(),
                userId().equals(user.getId()) && !defaultStyle,
                dataSource(),
                vectorOptions());
    }
}
