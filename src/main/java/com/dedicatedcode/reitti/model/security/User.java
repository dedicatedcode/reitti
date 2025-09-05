package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class User implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final Role role;
    private final Long version;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
