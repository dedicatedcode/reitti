package com.dedicatedcode.reitti.model;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;

public class ExternalUser extends User implements OidcUser {

    private final OidcIdToken token;
    private final OidcUserInfo userInfo;
    private final Map<String, Object> attributes;
    private final Map<String, Object> claims;

    public ExternalUser(User user, OidcUser oidcUser) {
        super(user.getId(), user.getUsername(), user.getPassword(), user.getDisplayName(), user.getRole(), user.getVersion());
        this.token = oidcUser.getIdToken();
        this.userInfo = oidcUser.getUserInfo();
        this.attributes = oidcUser.getAttributes();
        this.claims = oidcUser.getClaims();
    }

    @Override
    public Map<String, Object> getClaims() {
        return this.claims;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return this.userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return this.token;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    @Override
    public String getName() {
        return super.getUsername();
    }
}
