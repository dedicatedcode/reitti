package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TokenUser extends User {
    private final List<GrantedAuthority> authorities = new ArrayList<>();
    private final User user;
    private final MagicLinkResourceType type;
    private final Long resourceId;

    public TokenUser(User user, MagicLinkResourceType type, Long resourceId, List<String> additionalAuthorities) {
        this.user = user;
        this.type = type;
        this.resourceId = resourceId;
        this.authorities.addAll(additionalAuthorities.stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Long getId() {
        return user.getId();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public String getDisplayName() {
        return user.getDisplayName();
    }

    @Override
    public String getPassword() {
        throw new UnsupportedOperationException("TokenUser is not a password user");
    }

    @Override
    public Role getRole() {
        return Role.API_ACCESS;
    }

    @Override
    public String getProfileUrl() {
        return user.getProfileUrl();
    }

    public boolean grantsAccessTo(MagicLinkResourceType type, Long resourceId){
        return this.type.equals(type) && (this.resourceId == null || this.resourceId.equals(resourceId));
    }
}
