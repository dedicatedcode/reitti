package com.dedicatedcode.reitti.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum Role {
    ADMIN,
    USER,
    API_ACCESS;

    public GrantedAuthority asAuthority() {
        return new SimpleGrantedAuthority( "ROLE_" + name());
    }
}
