package com.dedicatedcode.reitti.model.devices;

import java.io.Serializable;
import java.time.Instant;

public record Device(Long id, String name, boolean enabled, boolean showOnMap, String color, boolean defaultDevice, Instant createdAt,
                     Instant updatedAt, Long version) implements Serializable {

}
