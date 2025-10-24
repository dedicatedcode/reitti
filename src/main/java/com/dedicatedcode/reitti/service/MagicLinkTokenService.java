package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class MagicLinkTokenService {
    
    private final MagicLinkJdbcService magicLinkJdbcService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public MagicLinkTokenService(MagicLinkJdbcService magicLinkJdbcService, PasswordEncoder passwordEncoder) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.passwordEncoder = passwordEncoder;
    }
    
    public String createMapShareToken(User user, String name, MagicLinkAccessLevel accessLevel, Instant expiryInstant) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = passwordEncoder.encode(rawToken);

        MagicLinkToken token = new MagicLinkToken(null, name, tokenHash, accessLevel, expiryInstant, null, null, false);
        magicLinkJdbcService.create(user, token);
        return rawToken;
    }

    public String createMemoryShareToken(User user, Long memoryId, MagicLinkAccessLevel accessLevel, int validDays) {
        // Generate a secure random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // Hash the token for storage
        String tokenHash = passwordEncoder.encode(rawToken);
        
        String tokenName = "Memory " + memoryId + " - " + (accessLevel == MagicLinkAccessLevel.MEMORY_VIEW_ONLY ? "View Only" : "Edit Access");
        Instant expiryDate = validDays > 0 ? Instant.now().plus(validDays, ChronoUnit.DAYS) : null;
        
        MagicLinkToken token = new MagicLinkToken(
            null,
            tokenName,
            tokenHash,
            accessLevel,
            expiryDate,
            MagicLinkResourceType.MEMORY,
            memoryId,
            Instant.now(),
            null,
            false
        );
        
        magicLinkJdbcService.create(user, token);
        return rawToken;
    }
    
    public List<MagicLinkToken> getTokensForUser(User user) {
        return magicLinkJdbcService.findByUser(user);
    }
    
    public Optional<MagicLinkToken> validateToken(String rawToken) {
        return magicLinkJdbcService.findByRawToken(rawToken);
    }
    
    public void markTokenAsUsed(long tokenId) {
        magicLinkJdbcService.updateLastUsed(tokenId);
    }
    
    public void deleteToken(long tokenId) {
        magicLinkJdbcService.delete(tokenId);
    }
}
