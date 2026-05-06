package com.dedicatedcode.reitti.dto.map;

import java.util.List;

public record MapStyleSettingsDTO(
        String activeStyleId,
        List<MapStyleConfigDTO> customStyles
) {
}
