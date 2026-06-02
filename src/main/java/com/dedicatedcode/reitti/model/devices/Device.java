package com.dedicatedcode.reitti.model.devices;

import java.io.Serializable;
import java.time.Instant;

public record Device(Long id, String name, boolean enabled, boolean showOnMap, String color, boolean defaultDevice, Instant createdAt,
                     Instant updatedAt, Long version) implements Serializable {

    public Device withDefaultDevice(boolean defaultDevice) {
        return new Device(id, name, enabled, showOnMap, color, defaultDevice, createdAt, updatedAt, version);
    }
}
