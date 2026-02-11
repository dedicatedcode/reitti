package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.dto.area.AreaBounds;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Helper class to find stored geofences.
 * <p>
 * Can be extended to implement/support boundary lookup.
 */
public abstract class AreaBoundaryLookupBase
{

    protected final AreaJdbcService areaJdbcService;

    protected AreaBoundaryLookupBase(AreaJdbcService areaJdbcService) {
        this.areaJdbcService = areaJdbcService;
    }

    protected Optional<AreaBounds> getGeoFence(List<AreaDescription> parentBoundaries) {
        if (parentBoundaries.isEmpty()) {
            return Optional.empty();
        }
        // Sort: biggest boundary to smallest
        parentBoundaries.sort(Comparator.comparing(AreaDescription::type));
        Long id = null;
        for (var boundary : parentBoundaries) {
            Optional<Long> foundId;
            foundId = areaJdbcService.getAreaId(boundary, id);
            if(foundId.isPresent()) {
                id = foundId.get();
            }
        }
        if(id == null) {
            return Optional.empty();
        }
        return areaJdbcService.getAreaBestGeoFence(id);
    }
}
