package com.dedicatedcode.reitti.dto.area;

/**
 * Describes a simple bounding box with global using WGS84 coordinates. (SRID: 4326)
 */
public record AreaBounds(double minLat, double maxLat, double minLon, double maxLon){}
