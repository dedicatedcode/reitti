package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class UserMapStyleJdbcServiceTest {

    @Autowired
    private UserMapStyleJdbcService service;

    @Autowired
    private TestingService testingService;

    @Test
    void shouldCreateAndFindStyleByUser() {
        User user = testingService.randomUser();
        UserMapStyle style = createTestStyle(user, null);

        UserMapStyle saved = service.save(user, style);
        assertNotNull(saved.id());
        assertEquals(style.name(), saved.name());

        Optional<UserMapStyle> found = service.findById(user, saved.id());
        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
    }

    @Test
    void shouldUpdateStyleOwnedByUser() {
        User user = testingService.randomUser();
        UserMapStyle style = createTestStyle(user, null);
        UserMapStyle saved = service.save(user, style);

        UserMapStyle updated = new UserMapStyle(
                saved.id(),
                user.getId(),
                "Updated Name",
                saved.mapType(),
                saved.styleInputType(),
                saved.rasterSourceInputType(),
                saved.styleJson(),
                saved.styleUrl(),
                saved.dataSource(),
                saved.vectorOptions(),
                saved.shared(),
                saved.defaultStyle(),
                saved.version()
        );
        UserMapStyle result = service.save(user, updated);
        assertEquals("Updated Name", result.name());
    }

    @Test
    void shouldDeleteStyleOwnedByUser() {
        User user = testingService.randomUser();
        UserMapStyle style = createTestStyle(user, null);
        UserMapStyle saved = service.save(user, style);

        service.delete(user, saved.id());

        Optional<UserMapStyle> found = service.findById(user, saved.id());
        assertFalse(found.isPresent());
    }

    @Test
    void shouldNotAllowModificationOfDefaultStyle() {
        User user = testingService.randomUser();

        // Retrieve the set of built‑in default styles
        List<UserMapStyle> allStyles = service.findAll(user);
        UserMapStyle defaultStyle = allStyles.stream()
                .filter(UserMapStyle::defaultStyle)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No default style found. Make sure the migration V101 has been applied."));

        assertThrows(UnsupportedOperationException.class, () -> {
            // Attempt to update the default style
            UserMapStyle updateAttempt = new UserMapStyle(
                    defaultStyle.id(),
                    user.getId(),
                    "Illegal Update",
                    defaultStyle.mapType(),
                    defaultStyle.styleInputType(),
                    defaultStyle.rasterSourceInputType(),
                    defaultStyle.styleJson(),
                    defaultStyle.styleUrl(),
                    defaultStyle.dataSource(),
                    defaultStyle.vectorOptions(),
                    defaultStyle.shared(),
                    defaultStyle.defaultStyle(),
                    defaultStyle.version()
            );
            service.save(user, updateAttempt);
        });
    }

    @Test
    void shouldGetAndSetActiveStyleId() {
        User user = testingService.randomUser();

        Long defaultStyleId = service.getActiveStyleId(user);
        Optional<UserMapStyle> defualtStle = this.service.findById(user, defaultStyleId);
        assertTrue(defualtStle.isPresent());
        assertEquals("Reitti", defualtStle.get().name());

        Long newActiveId = 123L;
        service.setActiveStyleId(user, newActiveId);

        assertThat(service.getActiveStyleId(user))
                .isEqualTo(newActiveId);
    }

    @Test
    void shouldResetActiveStyleOnDeletion() {
        User user = testingService.randomUser();

        createTestStyle(user, 123L, true);
        createTestStyle(testingService.admin(), 124L, true);

        Long defaultStyleId = service.getActiveStyleId(user);
        Optional<UserMapStyle> defualtStle = this.service.findById(user, defaultStyleId);
        assertTrue(defualtStle.isPresent());
        assertEquals("Reitti", defualtStle.get().name());

        Long newActiveId = 123L;
        service.setActiveStyleId(user, newActiveId);
        assertThat(service.getActiveStyleId(user)).isEqualTo(newActiveId);

        this.service.delete(user, 123L);
        assertThat(service.getActiveStyleId(user)).isEqualTo(defaultStyleId);

        this.service.setActiveStyleId(testingService.admin(), 124L);
        this.service.delete(testingService.admin(), 124L);
        assertThat(service.getActiveStyleId(user)).isEqualTo(defaultStyleId);

    }

    @Test
    void deletingNonExistentStyleDoesNotThrow() {
        User user = testingService.randomUser();
        assertDoesNotThrow(() -> service.delete(user, 9999L));
    }

    // ------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------

    private UserMapStyle createTestStyle(User user, Long id) {
        return createTestStyle(user, id, false);
    }

    private UserMapStyle createTestStyle(User user, Long id, boolean defaultStyle) {
        MapStyleDataSource dataSource = new MapStyleDataSource(
                "test-source",
                "tile",
                null,
                "https://tile.example.com/{z}/{x}/{y}.pbf",
                "Test attribution",
                0,
                18,
                512,
                "xyz",
                false
        );
        MapStyleVectorOptions vectorOptions = new MapStyleVectorOptions(null, null, null);
        return new UserMapStyle(
                id,
                user.getId(),
                "Test Style",
                "raster",
                "json",
                "url-template",
                "{\"version\":8,\"sources\":{}}",
                null,
                dataSource,
                vectorOptions,
                false,
                defaultStyle,
                0L
        );
    }
}
