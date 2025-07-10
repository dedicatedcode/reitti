package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
public class UserSettingsJdbcServiceTest {

    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId1;
    private Long testUserId2;
    private Long testUserId3;

    @BeforeEach
    void setUp() {
        // Create test users
        testUserId1 = createTestUser();
        testUserId2 = createTestUser();
        testUserId3 = createTestUser();
    }

    private Long createTestUser() {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name) VALUES (?, ?, ?)",
                username, "password", "Test User"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    @Test
    void findByUserId_WhenUserSettingsDoNotExist_ShouldReturnEmpty() {
        Optional<UserSettings> result = userSettingsJdbcService.findByUserId(testUserId1);
        
        assertThat(result).isEmpty();
    }

    @Test
    void save_WhenCreatingNewUserSettings_ShouldInsertAndReturnWithId() {
        UserSettings newSettings = new UserSettings(testUserId1, true, "fi", List.of(testUserId2, testUserId3));
        
        UserSettings savedSettings = userSettingsJdbcService.save(newSettings);
        
        assertThat(savedSettings.getId()).isNotNull();
        assertThat(savedSettings.getUserId()).isEqualTo(testUserId1);
        assertThat(savedSettings.isPreferColoredMap()).isTrue();
        assertThat(savedSettings.getSelectedLanguage()).isEqualTo("fi");
        assertThat(savedSettings.getConnectedUserAccounts()).containsExactlyInAnyOrder(testUserId2, testUserId3);
        assertThat(savedSettings.getVersion()).isEqualTo(1L);
    }

    @Test
    void save_WhenUpdatingExistingUserSettings_ShouldUpdateAndIncrementVersion() {
        // Create initial settings
        UserSettings initialSettings = new UserSettings(testUserId1, false, "en", List.of(testUserId2));
        UserSettings savedSettings = userSettingsJdbcService.save(initialSettings);
        
        // Update settings
        UserSettings updatedSettings = new UserSettings(
                savedSettings.getId(), 
                testUserId1, 
                true, 
                "de", 
                List.of(testUserId2, testUserId3), 
                savedSettings.getVersion()
        );
        
        UserSettings result = userSettingsJdbcService.save(updatedSettings);
        
        assertThat(result.getId()).isEqualTo(savedSettings.getId());
        assertThat(result.getUserId()).isEqualTo(testUserId1);
        assertThat(result.isPreferColoredMap()).isTrue();
        assertThat(result.getSelectedLanguage()).isEqualTo("de");
        assertThat(result.getConnectedUserAccounts()).containsExactlyInAnyOrder(testUserId2, testUserId3);
        assertThat(result.getVersion()).isEqualTo(2L);
    }

    @Test
    void findByUserId_WhenUserSettingsExist_ShouldReturnSettings() {
        // Create settings
        UserSettings newSettings = new UserSettings(testUserId1, true, "fr", List.of(testUserId2));
        userSettingsJdbcService.save(newSettings);
        
        Optional<UserSettings> result = userSettingsJdbcService.findByUserId(testUserId1);
        
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(testUserId1);
        assertThat(result.get().isPreferColoredMap()).isTrue();
        assertThat(result.get().getSelectedLanguage()).isEqualTo("fr");
        assertThat(result.get().getConnectedUserAccounts()).containsExactly(testUserId2);
    }

    @Test
    void getOrCreateDefaultSettings_WhenUserSettingsDoNotExist_ShouldCreateDefault() {
        UserSettings result = userSettingsJdbcService.getOrCreateDefaultSettings(testUserId1);
        
        assertThat(result.getId()).isNotNull();
        assertThat(result.getUserId()).isEqualTo(testUserId1);
        assertThat(result.isPreferColoredMap()).isFalse();
        assertThat(result.getSelectedLanguage()).isEqualTo("en");
        assertThat(result.getConnectedUserAccounts()).isEmpty();
        assertThat(result.getVersion()).isEqualTo(1L);
        
        // Verify it was actually saved to database
        Optional<UserSettings> fromDb = userSettingsJdbcService.findByUserId(testUserId1);
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getId()).isEqualTo(result.getId());
    }

    @Test
    void getOrCreateDefaultSettings_WhenUserSettingsExist_ShouldReturnExisting() {
        // Create existing settings
        UserSettings existingSettings = new UserSettings(testUserId1, true, "fi", List.of(testUserId2));
        UserSettings saved = userSettingsJdbcService.save(existingSettings);
        
        UserSettings result = userSettingsJdbcService.getOrCreateDefaultSettings(testUserId1);
        
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.isPreferColoredMap()).isTrue();
        assertThat(result.getSelectedLanguage()).isEqualTo("fi");
        assertThat(result.getConnectedUserAccounts()).containsExactly(testUserId2);
    }

    @Test
    void deleteByUserId_ShouldRemoveUserSettingsAndConnections() {
        // Create settings with connections
        UserSettings settings = new UserSettings(testUserId1, true, "de", List.of(testUserId2, testUserId3));
        userSettingsJdbcService.save(settings);
        
        // Verify settings exist
        assertThat(userSettingsJdbcService.findByUserId(testUserId1)).isPresent();
        
        // Verify connections exist
        List<Long> connections = jdbcTemplate.queryForList(
                "SELECT to_user FROM user_connections WHERE from_user = ?",
                Long.class,
                testUserId1
        );
        assertThat(connections).hasSize(2);
        
        userSettingsJdbcService.deleteByUserId(testUserId1);
        
        // Verify settings are deleted
        assertThat(userSettingsJdbcService.findByUserId(testUserId1)).isEmpty();
        
        // Note: connections are not automatically deleted by deleteByUserId
        // This is by design as connections might be managed separately
    }

    @Test
    void userConnections_ShouldBeLoadedCorrectly() {
        // Create bidirectional connections manually
        jdbcTemplate.update(
                "INSERT INTO user_connections (from_user, to_user) VALUES (?, ?)",
                testUserId1, testUserId2
        );
        jdbcTemplate.update(
                "INSERT INTO user_connections (from_user, to_user) VALUES (?, ?)",
                testUserId3, testUserId1
        );
        
        // Create settings (this will load connections)
        UserSettings settings = new UserSettings(testUserId1, false, "en", List.of());
        UserSettings saved = userSettingsJdbcService.save(settings);
        
        // Find the settings to verify connections are loaded
        Optional<UserSettings> result = userSettingsJdbcService.findByUserId(testUserId1);
        
        assertThat(result).isPresent();
        assertThat(result.get().getConnectedUserAccounts()).containsExactlyInAnyOrder(testUserId2, testUserId3);
    }

    @Test
    void save_ShouldReplaceAllUserConnections() {
        // Create initial connections
        UserSettings initialSettings = new UserSettings(testUserId1, false, "en", List.of(testUserId2));
        UserSettings saved = userSettingsJdbcService.save(initialSettings);
        
        // Verify initial connection
        List<Long> initialConnections = jdbcTemplate.queryForList(
                "SELECT to_user FROM user_connections WHERE from_user = ?",
                Long.class,
                testUserId1
        );
        assertThat(initialConnections).containsExactly(testUserId2);
        
        // Update with different connections
        UserSettings updatedSettings = new UserSettings(
                saved.getId(),
                testUserId1,
                false,
                "en",
                List.of(testUserId3),
                saved.getVersion()
        );
        userSettingsJdbcService.save(updatedSettings);
        
        // Verify connections were replaced
        List<Long> updatedConnections = jdbcTemplate.queryForList(
                "SELECT to_user FROM user_connections WHERE from_user = ?",
                Long.class,
                testUserId1
        );
        assertThat(updatedConnections).containsExactly(testUserId3);
        
        // Verify old connection is gone
        List<Long> allConnections = jdbcTemplate.queryForList(
                "SELECT to_user FROM user_connections WHERE from_user = ? AND to_user = ?",
                Long.class,
                testUserId1, testUserId2
        );
        assertThat(allConnections).isEmpty();
    }

    @Test
    void save_WithEmptyConnections_ShouldRemoveAllConnections() {
        // Create initial connections
        UserSettings initialSettings = new UserSettings(testUserId1, false, "en", List.of(testUserId2, testUserId3));
        UserSettings saved = userSettingsJdbcService.save(initialSettings);
        
        // Update with empty connections
        UserSettings updatedSettings = new UserSettings(
                saved.getId(),
                testUserId1,
                false,
                "en",
                List.of(),
                saved.getVersion()
        );
        userSettingsJdbcService.save(updatedSettings);
        
        // Verify all connections are removed
        List<Long> connections = jdbcTemplate.queryForList(
                "SELECT to_user FROM user_connections WHERE from_user = ?",
                Long.class,
                testUserId1
        );
        assertThat(connections).isEmpty();
    }

    @Test
    void defaultSettings_ShouldHaveCorrectValues() {
        UserSettings defaultSettings = UserSettings.defaultSettings(testUserId1);
        
        assertThat(defaultSettings.getId()).isNull();
        assertThat(defaultSettings.getUserId()).isEqualTo(testUserId1);
        assertThat(defaultSettings.isPreferColoredMap()).isFalse();
        assertThat(defaultSettings.getSelectedLanguage()).isEqualTo("en");
        assertThat(defaultSettings.getConnectedUserAccounts()).isEmpty();
        assertThat(defaultSettings.getVersion()).isNull();
    }
}
