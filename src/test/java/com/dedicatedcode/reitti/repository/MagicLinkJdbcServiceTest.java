package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MagicLinkJdbcServiceTest {

    @Autowired
    private MagicLinkJdbcService magicLinkJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.admin();
        // Clean up any existing magic link tokens for the test user
        List<MagicLinkToken> existingTokens = magicLinkJdbcService.findByUser(testUser);
        existingTokens.forEach(token -> magicLinkJdbcService.delete(token.getId()));
    }

    @Test
    void shouldCreateMagicLinkToken() {
        // Given
        String tokenHash = "test-token-hash-123";
        MagicLinkAccessLevel accessLevel = MagicLinkAccessLevel.FULL_ACCESS;
        Instant expiryDate = Instant.now().plus(1, ChronoUnit.HOURS);
        
        MagicLinkToken tokenToCreate = new MagicLinkToken(
                null,
                UUID.randomUUID().toString(),
            tokenHash,
            accessLevel,
            expiryDate,
            Instant.now(),
            null,
            false
        );

        // When
        MagicLinkToken createdToken = magicLinkJdbcService.create(testUser, tokenToCreate);

        // Then
        assertThat(createdToken).isNotNull();
        assertThat(createdToken.getId()).isGreaterThan(0);
        assertThat(createdToken.getTokenHash()).isEqualTo(tokenHash);
        assertThat(createdToken.getAccessLevel()).isEqualTo(accessLevel);
        assertThat(createdToken.getExpiryDate()).isEqualTo(expiryDate);
        assertThat(createdToken.getCreatedAt()).isNotNull();
        assertThat(createdToken.getLastUsed()).isNull();
        assertThat(createdToken.isUsed()).isFalse();
    }

    @Test
    void shouldFindTokenById() {
        // Given
        MagicLinkToken token = createTestToken("find-by-id-token");

        // When
        Optional<MagicLinkToken> foundToken = magicLinkJdbcService.findById(token.getId());

        // Then
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getId()).isEqualTo(token.getId());
        assertThat(foundToken.get().getTokenHash()).isEqualTo(token.getTokenHash());
    }

    @Test
    void shouldFindTokenByTokenHash() {
        // Given
        String tokenHash = "find-by-hash-token";
        MagicLinkToken token = createTestToken(tokenHash);

        // When
        Optional<MagicLinkToken> foundToken = magicLinkJdbcService.findByTokenHash(tokenHash);

        // Then
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getId()).isEqualTo(token.getId());
        assertThat(foundToken.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    void shouldReturnEmptyWhenTokenNotFound() {
        // When
        Optional<MagicLinkToken> foundToken = magicLinkJdbcService.findByTokenHash("non-existent-token");

        // Then
        assertThat(foundToken).isEmpty();
    }

    @Test
    void shouldFindTokensByUser() {
        // Given
        createTestToken("user-token-1");
        createTestToken("user-token-2");

        // When
        List<MagicLinkToken> userTokens = magicLinkJdbcService.findByUser(testUser);

        // Then
        assertThat(userTokens).hasSize(2);
        assertThat(userTokens).extracting(MagicLinkToken::getTokenHash)
            .containsExactlyInAnyOrder("user-token-1", "user-token-2");
    }

    @Test
    void shouldUpdateToken() {
        // Given
        MagicLinkToken originalToken = createTestToken("update-token");
        Instant lastUsed = Instant.now();
        
        MagicLinkToken updatedToken = new MagicLinkToken(
            originalToken.getId(),
                originalToken.getName(),
            originalToken.getTokenHash(),
                MagicLinkAccessLevel.ONLY_LIVE, // Changed access level
            originalToken.getExpiryDate(),
            originalToken.getCreatedAt(),
            lastUsed, // Set last used
            true
        );

        // When
        Optional<MagicLinkToken> result = magicLinkJdbcService.update(updatedToken);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAccessLevel()).isEqualTo(MagicLinkAccessLevel.ONLY_LIVE);
        assertThat(result.get().getLastUsed()).isNotNull();
        assertThat(result.get().isUsed()).isTrue();
    }

    @Test
    void shouldDeleteToken() {
        // Given
        MagicLinkToken token = createTestToken("delete-token");
        long tokenId = token.getId();

        // When
        magicLinkJdbcService.delete(tokenId);

        // Then
        Optional<MagicLinkToken> foundToken = magicLinkJdbcService.findById(tokenId);
        assertThat(foundToken).isEmpty();
    }

    @Test
    void shouldHandleMultipleAccessLevels() {
        // Given & When
        MagicLinkToken readOnlyToken = createTestTokenWithAccessLevel("readonly", MagicLinkAccessLevel.FULL_ACCESS);
        MagicLinkToken fullAccessToken = createTestTokenWithAccessLevel("fullaccess", MagicLinkAccessLevel.ONLY_LIVE);

        // Then
        assertThat(readOnlyToken.getAccessLevel()).isEqualTo(MagicLinkAccessLevel.FULL_ACCESS);
        assertThat(fullAccessToken.getAccessLevel()).isEqualTo(MagicLinkAccessLevel.ONLY_LIVE);
    }

    private MagicLinkToken createTestToken(String tokenHash) {
        return createTestTokenWithAccessLevel(tokenHash, MagicLinkAccessLevel.FULL_ACCESS);
    }

    private MagicLinkToken createTestTokenWithAccessLevel(String tokenHash, MagicLinkAccessLevel accessLevel) {
        MagicLinkToken token = new MagicLinkToken(
            null,
            UUID.randomUUID().toString(),
            tokenHash,
            accessLevel,
            Instant.now().plus(1, ChronoUnit.HOURS),
            Instant.now(),
            null,
            false
        );
        return magicLinkJdbcService.create(testUser, token);
    }
}
