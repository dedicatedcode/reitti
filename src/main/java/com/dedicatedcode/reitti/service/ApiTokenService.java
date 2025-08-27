package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ApiTokenJdbcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    private final ApiTokenJdbcService apiTokenJdbcService;

    @Autowired
    public ApiTokenService(ApiTokenJdbcService apiTokenJdbcService) {
        this.apiTokenJdbcService = apiTokenJdbcService;
    }

    public Optional<User> getUserByToken(String token) {
        return apiTokenJdbcService.findByToken(token)
                .map(this::updateLastUsed)
                .map(ApiToken::getUser);
    }

    public ApiToken createToken(User user, String name) {
        ApiToken token = new ApiToken(user, name);
        return apiTokenJdbcService.save(token);
    }

    public void deleteToken(Long tokenId) {
        apiTokenJdbcService.deleteById(tokenId);
    }

    private ApiToken updateLastUsed(ApiToken token) {
        return apiTokenJdbcService.save(token.withLastUsedAt(Instant.now()));
    }

    public List<ApiToken> getTokensForUser(User currentUser) {
        return this.apiTokenJdbcService.findByUser(currentUser);
    }

    public List<?> getRecentUsagesForUser(User user, int maxRows) {
        return this.apiTokenJdbcService.getUsages(user, maxRows);
    }

    public void trackUsage(String token, String requestPath, String remoteIp) {
        this.apiTokenJdbcService.trackUsage(token, requestPath, remoteIp);
    }
}
