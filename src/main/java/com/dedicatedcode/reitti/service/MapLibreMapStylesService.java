package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.MapLibreStyleDefinition;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MapLibreMapStylesService {
    private static final Logger log = LoggerFactory.getLogger(MapLibreMapStylesService.class);

    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final ContextPathHolder contextPathHolder;

    public MapLibreMapStylesService(
            UserMapStyleJdbcService userMapStyleJdbcService,
            ContextPathHolder contextPathHolder) {
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.contextPathHolder = contextPathHolder;
    }

    @Cacheable("mapStyles")
    public List<MapLibreStyleDefinition> getConfig(User user) {
        List<UserMapStyle> all = this.userMapStyleJdbcService.findAll(user);
        List<MapLibreStyleDefinition> definitions = new ArrayList<>();
        for (UserMapStyle style : all) {
            try {
                definitions.add(buildStyleDefinition(style));
            } catch (Exception e) {
                log.warn("Failed to build style definition for style [{}]: {}", style.id(), e.getMessage());
            }
        }
        return definitions;
    }

    private MapLibreStyleDefinition buildStyleDefinition(UserMapStyle style) {
        String styleId = String.valueOf(style.id());
        String contextPath = contextPathHolder.getContextPath();

        return new MapLibreStyleDefinition(
                styleId,
                style.name(),
                style.mapType(),
                "url",
                contextPath + "/api/v1/tiles/styles/" + styleId + "/style.json",
                buildCapabilities(style));

    }

    private Map<String, Object> buildCapabilities(UserMapStyle style) {
        Map<String, Object> caps = new HashMap<>();
        if ("vector".equals(style.mapType())) {
            caps.put("terrainSourceId", "reitti-terrain-source");
            caps.put("hillshadeLayerId", "reitti-terrain-hillshade");
            caps.put("satelliteLayerId", "reitti-satellite-layer");
            caps.put("building3dLayerIds", Collections.singletonList("reitti-building-3d"));
        }
        return caps;
    }
}