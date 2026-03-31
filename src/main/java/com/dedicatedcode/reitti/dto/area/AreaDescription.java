package com.dedicatedcode.reitti.dto.area;

/**
 * Simple area description.
 * </p>
 * An area is a geographical region that can be used to categorize or group locations.
 * Without a boundary or parent link this object does *NOT* describe a unique specific area.
 * There exist areas with same type and name which are different. This difference is *NOT* mapped by this class alone.
 * @param type Type of the area
 * @param name Name of the area
 */
public record AreaDescription(AreaType type, String name){}
