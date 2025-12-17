package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TilesCustomizationProvider {
    private final UserSettingsDTO.TilesCustomizationDTO tilesConfiguration;

    public TilesCustomizationProvider(
            @Value("${reitti.ui.tile.cache.url:null}") String cacheUrl,
            @Value("${reitti.ui.tiles.default.service}") String defaultService,
            @Value("${reitti.ui.tiles.default.attribution}") String defaultAttribution,
            @Value("${reitti.ui.tiles.custom.service:}") String customService,
            @Value("${reitti.ui.tiles.custom.attribution:}") String customAttribution) {
        String serviceUrl;
        if (StringUtils.hasText(cacheUrl)) {
            serviceUrl = "/api/v1/tiles/{z}/{x}/{y}.png";
        } else if (StringUtils.hasText(customService)) {
            serviceUrl = customService;
        } else {
            serviceUrl = defaultService;
        }
        String attribution = StringUtils.hasText(customAttribution) ? customAttribution : defaultAttribution;
        this.tilesConfiguration = new UserSettingsDTO.TilesCustomizationDTO(serviceUrl, attribution);
    }

    public UserSettingsDTO.TilesCustomizationDTO getTilesConfiguration() {
        return this.tilesConfiguration;
    }
}
