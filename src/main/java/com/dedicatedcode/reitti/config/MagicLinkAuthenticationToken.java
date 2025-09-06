package com.dedicatedcode.reitti.config;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class MagicLinkAuthenticationToken extends UsernamePasswordAuthenticationToken {
    private final Long magicLinkTokenId;
    
    public MagicLinkAuthenticationToken(Object principal, Object credentials, 
                                      Collection<? extends GrantedAuthority> authorities,
                                      Long magicLinkTokenId) {
        super(principal, credentials, authorities);
        this.magicLinkTokenId = magicLinkTokenId;
    }
    
    public Long getMagicLinkTokenId() {
        return magicLinkTokenId;
    }
}
