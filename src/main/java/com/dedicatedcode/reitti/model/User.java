package com.dedicatedcode.reitti.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class User implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final Long version;

    public User() {
        this(null, null, null, null, null);
    }

    public User(String username, String displayName) {
        this(null, username, null, displayName, null);
    }

    public User(Long id, String username, String password, String displayName, Long version) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
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
        return List.of(new SimpleGrantedAuthority("USER"));
    }

    public String getPassword() {
        return password;
    }

    public Long getVersion() {
        return version;
    }

    // Wither methods
    public User withPassword(String password) {
        return new User(this.id, this.username, password, this.displayName, this.version);
    }

    public User withDisplayName(String displayName) {
        return new User(this.id, this.username, this.password, displayName, this.version);
    }

    public User withVersion(Long version) {
        return new User(this.id, this.username, this.password, this.displayName, version);
    }

}
