package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class UserDeviceAuthenticationToken extends AbstractAuthenticationToken {

    private final User user;
    private final Device device;

    public UserDeviceAuthenticationToken(User user, Device device) {
        super(user.getAuthorities());
        this.user = user;
        this.device = device;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return new DeviceTokenUser(user, device);
    }
}
