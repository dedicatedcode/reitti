package com.dedicatedcode.reitti.dto.area;

import java.io.Serializable;
import java.util.List;

public record AreaMappingRequest(long significantPlaceId, List<AreaDescription> areaDescriptions) implements Serializable
{}
