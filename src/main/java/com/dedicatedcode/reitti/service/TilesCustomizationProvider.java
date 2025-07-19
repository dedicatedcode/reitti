package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TilesCustomizationProvider {
    private final String defaultService;
    private final String defaultAttribution;

    private final String customService;
    private final String customAttribution;

    public TilesCustomizationProvider(
            @Value("${reitti.ui.tiles.default.service}") String defaultService,
            @Value("${reitti.ui.tiles.default.attribution}") String defaultAttribution,
            @Value("${reitti.ui.tiles.custom.service:}") String customService,
            @Value("${reitti.ui.tiles.custom.attribution:}") String customAttribution) {
        this.defaultService = defaultService;
        this.defaultAttribution = defaultAttribution;
        this.customService = customService;
        this.customAttribution = customAttribution;
    }

    public UserSettingsDTO.TilesCustomizationDTO getTilesConfiguration() {
        return new UserSettingsDTO.TilesCustomizationDTO(
                StringUtils.hasText(customService) ? customService : defaultService,
                StringUtils.hasText(customAttribution) ? customAttribution : defaultAttribution
        );
    }
}
