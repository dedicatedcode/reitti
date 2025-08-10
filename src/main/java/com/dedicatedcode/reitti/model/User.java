package com.dedicatedcode.reitti.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class User implements UserDetails, OidcUser {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final Role role;
    private final Long version;
    private OidcIdToken token = null;
    private OidcUserInfo userInfo = null;
    private Map<String, Object> attributes = null;
    private Map<String, Object> claims = null;

    public User() {
        this(null, null, null, null, Role.USER, null);
    }

    public User(String username, String displayName) {
        this(null, username, null, displayName, Role.USER, null);
    }

    public User(Long id, String username, String password, String displayName, Role role, Long version) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public Long getVersion() {
        return version;
    }

    // Wither methods
    public User withPassword(String password) {
        return new User(this.id, this.username, password, this.displayName, this.role, this.version);
    }

    public User withDisplayName(String displayName) {
        return new User(this.id, this.username, this.password, displayName, this.role, this.version);
    }

    public User withVersion(Long version) {
        return new User(this.id, this.username, this.password, this.displayName, this.role, version);
    }

    public User withRole(Role role) {
        return new User(this.id, this.username, this.password, this.displayName, role, this.version);
    }

    public void setOidcUser(OidcUser oidcUser) {
        token = oidcUser.getIdToken();
        userInfo = oidcUser.getUserInfo();
        attributes = oidcUser.getAttributes();
        claims = oidcUser.getClaims();
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Map<String, Object> getClaims() {
        return claims;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return token;
    }
}
