package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ApiTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    private final ApiTokenRepository apiTokenRepository;

    @Autowired
    public ApiTokenService(ApiTokenRepository apiTokenRepository) {
        this.apiTokenRepository = apiTokenRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByToken(String token) {
        return apiTokenRepository.findByToken(token)
                .map(this::updateLastUsed)
                .map(ApiToken::getUser);
    }

    @Transactional
    public ApiToken createToken(User user, String name) {
        ApiToken token = new ApiToken();
        token.setUser(user);
        token.setName(name);
        return apiTokenRepository.save(token);
    }

    @Transactional
    public void deleteToken(Long tokenId) {
        apiTokenRepository.deleteById(tokenId);
    }

    private ApiToken updateLastUsed(ApiToken token) {
        token.setLastUsedAt(Instant.now());
        return apiTokenRepository.save(token);
    }

    public List<ApiToken> getTokensForUser(User currentUser) {
        return this.apiTokenRepository.findByUser(currentUser);
    }
}
