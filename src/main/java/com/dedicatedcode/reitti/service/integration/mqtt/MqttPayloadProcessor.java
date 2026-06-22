package com.dedicatedcode.reitti.service.integration.mqtt;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;

public interface MqttPayloadProcessor {
    PayloadType getSupportedType();
    void process(User user, Device device, byte[] payload);
}
