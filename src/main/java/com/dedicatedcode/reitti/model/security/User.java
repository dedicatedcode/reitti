package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
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
    private final String profileUrl;
    private final String externalId;
    private final Role role;
    private final UserType userType;
    private final Long version;

    public User() {
        this(null, null, null, null, null, null, Role.USER, UserType.NORMAL, null);
    }

    public User(String username, String displayName) {
        this(null, username, null, displayName, null, null, Role.USER, UserType.NORMAL, null);
    }

    public User(Long id, String username, String password, String displayName, String profileUrl, String externalId, Role role, UserType userType, Long version) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.profileUrl = profileUrl;
        this.externalId = externalId;
        this.role = role;
        this.userType = userType;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
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

    public UserType getUserType() {
        return userType;
    }

    public String getProfileUrl() {
        return this.profileUrl;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getVersion() {
        return version;
    }

    public User withPassword(String password) {
        return new User(this.id, this.username, password, this.displayName, this.profileUrl, this.externalId, this.role, this.userType, this.version);
    }

    public User withDisplayName(String displayName) {
        return new User(this.id, this.username, this.password, displayName, this.profileUrl, this.externalId, this.role, this.userType, this.version);
    }

    public User withVersion(Long version) {
        return new User(this.id, this.username, this.password, this.displayName, this.profileUrl, this.externalId, this.role, this.userType, version);
    }

    public User withRole(Role role) {
        return new User(this.id, this.username, this.password, this.displayName, this.profileUrl, this.externalId, role, this.userType, this.version);
    }

    public User withUserType(UserType userType) {
        return new User(this.id, this.username, this.password, this.displayName, this.profileUrl, this.externalId, this.role, userType, this.version);
    }

    public User withUsername(String username) {
        return new User(this.id, username, this.password, this.displayName, this.profileUrl, this.externalId, this.role, this.userType, this.version);
    }

    public User withProfileUrl(String profileUrl) {
        return new User(this.id, this.username, this.password, this.displayName, profileUrl, this.externalId, this.role, this.userType, this.version);
    }

    public User withExternalId(String externalId) {
        return new User(this.id, this.username, this.password, this.displayName, this.profileUrl, externalId, this.role, this.userType, this.version);
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

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                '}';
    }
}
