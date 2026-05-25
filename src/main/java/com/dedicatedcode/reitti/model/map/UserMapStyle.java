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
        boolean shared,
        Long version
) {
    public static UserMapStyle defaultReittiStyle() {
        return new UserMapStyle(-1L, null, "Reitti", "vector", "url", "url", null, "/styles/reitti.json", null, null, false, null);
    }

    public String frontendId() {
        return "custom-" + id;
    }

    public String styleInput() {
        return styleJson != null ? styleJson : styleUrl;
    }

    public MapStyleConfigDTO toDto(User user) {
        return new MapStyleConfigDTO(
                id + "",
                name(),
                mapType(),
                styleInputType(),
                rasterSourceInputType(),
                styleUrl,
                styleInput(),
                id != null && id != -1,
                shared(),
                userId().equals(user.getId()),
                dataSource(),
                vectorOptions());
    }
}
