package com.dedicatedcode.reitti.event;

import java.io.Serializable;

public record SignificantPlaceCreatedEvent(Long placeId, Double latitude, Double longitude) implements Serializable {
}
