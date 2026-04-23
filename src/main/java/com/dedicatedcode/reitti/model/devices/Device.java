package com.dedicatedcode.reitti.model.devices;

import java.time.Instant;

public record Device(Long id, String name, boolean enabled, boolean showOnMap, String color, Instant createdAt,
                     Instant updatedAt, Long version) {
}
