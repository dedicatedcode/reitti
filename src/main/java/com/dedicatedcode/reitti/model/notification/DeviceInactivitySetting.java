package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;

public record DeviceInactivitySetting(Long id,
                                      Long userId,
                                      Long deviceId,
                                      int hours,
                                      Instant lastNotifiedAt,
                                      Instant createdAt,
                                      Instant updatedAt,
                                      Long version) {

    public static final int DEFAULT_HOURS = 24;
}
