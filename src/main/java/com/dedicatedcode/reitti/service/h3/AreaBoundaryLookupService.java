package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.dto.area.AreaBounds;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface AreaBoundaryLookupService extends Ordered
{

    /**
     * Returns the GeoJSON for a boundary.
     * <p>
     * If a geofence is provided, the search is restricted to areas within the geofence.
     * This greatly enhances the accuracy and reduces wrong matches.
     *
     * @param areaDescription Boundary to look up
     * @param areaBounds Geofence to restrict search to
     * @return GeoJson if found, empty otherwise
     */
    Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription, @org.springframework.lang.Nullable AreaBounds areaBounds)
        throws IOException, InterruptedException;

    /**
     * Returns the GeoJSON for a boundary.
     * <p>
     * If parents are provided, tries to use their stored geofences to limit the search area. This greatly enhances the
     * accuracy and reduces wrong matches.
     *
     * @param areaDescription Boundary to look up
     * @param parentAreaDescriptions Parent areas to restrict search to
     * @return GeoJson if found, empty otherwise
     */
    Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription, List<AreaDescription> parentAreaDescriptions)
        throws IOException, InterruptedException;
}
