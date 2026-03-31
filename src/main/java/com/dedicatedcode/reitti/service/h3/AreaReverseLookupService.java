package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.model.geo.GeoPoint;

import java.io.IOException;
import java.util.List;

public interface AreaReverseLookupService
{
    /**
     * Looks up the area hierarchy for a given position.
     * <p>
     * The result is in order: highest to lowest (e.g., country, state, county, city). Might be empty if no areas
     * are found.
     *
     * @param position The position to look up
     * @return List of areas in order from highest to lowest
     */
    List<AreaDescription> getAreaHierarchy(GeoPoint position) throws IOException, InterruptedException;
}

