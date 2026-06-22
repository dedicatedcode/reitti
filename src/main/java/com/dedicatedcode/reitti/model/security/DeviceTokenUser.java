package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.devices.Device;

import java.util.Optional;

public class DeviceTokenUser extends User {
    private final Device device;
    public DeviceTokenUser(User user, Device device) {
        super(user.getId(), user.getUsername(), user.getPassword(), user.getDisplayName(), user.getProfileUrl(), user.getExternalId(), user.getRole(), user.getVersion());
        this.device = device;
    }

    public Optional<Device> getDevice() {
        return Optional.ofNullable(device);
    }
}
