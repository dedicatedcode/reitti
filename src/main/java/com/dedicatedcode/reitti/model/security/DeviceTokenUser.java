package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.devices.Device;

public class DeviceTokenUser extends User {
    private final Device device;
    public DeviceTokenUser(User user, Device device) {
        super();
        this.device = device;
    }
}
