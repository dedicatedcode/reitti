package com.dedicatedcode.reitti.model.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum MagicLinkAccessLevel {
    FULL_ACCESS,
    ONLY_LIVE,
    ONLY_LAST_LOCATION,
    ONLY_LIVE_WITH_PHOTOS,
    MEMORY_VIEW_ONLY,
    MEMORY_EDIT_ACCESS;


    public GrantedAuthority asAuthority() {
        return new SimpleGrantedAuthority( "ROLE_MAGIC_LINK_" + name());
    }
}
