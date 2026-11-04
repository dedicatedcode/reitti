package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@IntegrationTest
class TransportationModesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private TransportModeJdbcService transportModeJdbcService;

    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void getTransportationModes_AsNormalUser_ShouldReturnTransportationModesPage() throws Exception {
        mockMvc.perform(get("/settings/transportation-modes").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes"))
                .andExpect(model().attribute("activeSection", "transportation-modes"))
                .andExpect(model().attribute("isAdmin", false))
                .andExpect(model().attributeExists("dataManagementEnabled"))
                .andExpect(model().attributeExists("configs"))
                .andExpect(model().attributeExists("availableModes"))
                .andExpect(model().attributeExists("unitSystem"))
                .andExpect(model().attributeExists("modeChangeSensitivity"))
                .andExpect(model().attributeExists("isImperial"));
    }

    @Test
    void getTransportationModes_AsAdminUser_ShouldReflectAdminInModel() throws Exception {
        User admin = testingService.admin();

        mockMvc.perform(get("/settings/transportation-modes").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes"))
                .andExpect(model().attribute("isAdmin", true));
    }

    @Test
    void getTransportationModes_WithImperialUnitSystem_ShouldSetIsImperialTrue() throws Exception {
        UserSettings current = userSettingsJdbcService.getOrCreateDefaultSettings(testUser.getId());
        userSettingsJdbcService.save(new UserSettings(
                current.getUserId(),
                current.getSelectedLanguage(),
                UnitSystem.IMPERIAL,
                current.getHomeLatitude(),
                current.getHomeLongitude(),
                current.getTimeZoneOverride(),
                current.getTimeDisplayMode(),
                current.getTimeMode(),
                current.getDayStartTime(),
                current.getCustomCss(),
                current.getLatestData(),
                current.getColor(),
                current.getVersion()
        ));

        mockMvc.perform(get("/settings/transportation-modes").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("unitSystem", UnitSystem.IMPERIAL))
                .andExpect(model().attribute("isImperial", true));
    }

    @Test
    void getTransportationModes_AsLiveDataOnlyUser_ShouldReturnUnavailablePage() throws Exception {
        User liveDataUser = testUser.withUserType(UserType.LIVE_DATA_ONLY);

        mockMvc.perform(get("/settings/transportation-modes").with(user(liveDataUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/unavailable"))
                .andExpect(model().attribute("activeSection", "transportation-modes"))
                .andExpect(model().attribute("isAdmin", false))
                .andExpect(model().attributeExists("dataManagementEnabled"))
                .andExpect(model().attributeDoesNotExist("configs"));
    }

    @Test
    void getCreateForm_ShouldReturnCreateFormFragmentWithAvailableModes() throws Exception {
        List<TransportModeConfig> userConfigs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0),
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 20.0)
        );
        transportModeJdbcService.setTransportModeConfigs(testUser, userConfigs);

        mockMvc.perform(get("/settings/transportation-modes/create-form").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/fragments/transportation-modes :: transportation-mode-edit-form"))
                .andExpect(model().attributeExists("availableModes", "unitSystem", "isImperial", "defaultColors"))
                .andExpect(model().attribute("isImperial", false));

        @SuppressWarnings("unchecked")
        List<TransportMode> availableModes = (List<TransportMode>) mockMvc.perform(get("/settings/transportation-modes/create-form").with(user(testUser)))
                .andReturn().getModelAndView().getModel().get("availableModes");

        assertThat(availableModes).doesNotContain(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.UNKNOWN);
        assertThat(availableModes).contains(TransportMode.DRIVING, TransportMode.TRANSIT);
    }

    @Test
    void getEditForm_ForExistingMode_ShouldReturnEditFormFragment() throws Exception {
        TransportModeConfig walkingConfig = new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0, "#ff6b6b", "fa-person-walking");
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(walkingConfig));

        mockMvc.perform(get("/settings/transportation-modes/{mode}/edit", TransportMode.WALKING.name()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/fragments/transportation-modes :: transportation-mode-edit-form"))
                .andExpect(model().attribute("config", walkingConfig))
                .andExpect(model().attribute("selectedColor", "#ff6b6b"))
                .andExpect(model().attributeExists("unitSystem", "isImperial", "defaultColors"));
    }

    @Test
    void getEditForm_ForNonExistentMode_ShouldThrowException() {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        assertThatThrownBy(() -> mockMvc.perform(get("/settings/transportation-modes/{mode}/edit", TransportMode.AIRPLANE.name()).with(user(testUser))))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addTransportMode_Metric_ShouldAddNewMode() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.CYCLING.name())
                        .param("minSpeed", "5.0")
                        .param("maxSpeed", "25.0")
                        .param("preferenceWeight", "1.5")
                        .param("enabled", "true")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .param("color", "#4ecdc4")
                        .param("icon", "fa-bicycle")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.added"))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> cycling = configs.stream().filter(c -> c.mode() == TransportMode.CYCLING).findFirst();
        assertThat(cycling).isPresent();
        assertThat(cycling.get().minKmh()).isEqualTo(5.0);
        assertThat(cycling.get().maxKmh()).isEqualTo(25.0);
        assertThat(cycling.get().preferenceWeight()).isEqualTo(1.5);
        assertThat(cycling.get().enabled()).isTrue();
        assertThat(cycling.get().color()).isEqualTo("#4ecdc4");
        assertThat(cycling.get().icon()).isEqualTo("fa-bicycle");
    }

    @Test
    void addTransportMode_Imperial_ShouldConvertSpeedsToKmh() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.DRIVING.name())
                        .param("minSpeed", "10.0")
                        .param("maxSpeed", "60.0")
                        .param("unitSystem", UnitSystem.IMPERIAL.name())
                        .param("color", "#45b7d1")
                        .param("icon", "fa-car")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.added"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> driving = configs.stream().filter(c -> c.mode() == TransportMode.DRIVING).findFirst();
        assertThat(driving).isPresent();
        assertThat(driving.get().minKmh()).isCloseTo(16.0934, within(0.001));
        assertThat(driving.get().maxKmh()).isCloseTo(96.5604, within(0.001));
    }

    @Test
    void addTransportMode_MissingRange_ShouldShowErrorMessage() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.TRANSIT.name())
                        .param("color", "#96ceb4")
                        .param("icon", "fa-bus")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("errorMessage", "transportation.modes.error.range.required"))
                .andExpect(model().attributeDoesNotExist("successMessage"));
    }

    @Test
    void addTransportMode_AlreadyExists_ShouldShowErrorMessage() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.WALKING.name())
                        .param("minSpeed", "5.0")
                        .param("maxSpeed", "10.0")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("errorMessage", "transportation.modes.error.already.exists"))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        assertThat(transportModeJdbcService.getTransportModeConfigs(testUser)).hasSize(1);
    }

    @Test
    void addTransportMode_InvalidRange_ShouldShowErrorMessage() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.CYCLING.name())
                        .param("minSpeed", "20.0")
                        .param("maxSpeed", "10.0")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("errorMessage", "transportation.modes.error.invalid.range"))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        assertThat(transportModeJdbcService.getTransportModeConfigs(testUser)).hasSize(1);
    }

    @Test
    void addTransportMode_OverlappingRange_ShouldBeAllowed() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 8.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/add").with(csrf())
                        .param("mode", TransportMode.CYCLING.name())
                        .param("minSpeed", "5.0")
                        .param("maxSpeed", "25.0")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("successMessage", "transportation.modes.success.added"));

        assertThat(transportModeJdbcService.getTransportModeConfigs(testUser)).hasSize(2);
    }

    @Test
    void updateTransportMode_Metric_ShouldUpdateConfig() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 20.0, "#111111", "fa-old")
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/update", TransportMode.CYCLING.name()).with(csrf())
                        .param("minSpeed", "10.0")
                        .param("maxSpeed", "30.0")
                        .param("preferenceWeight", "1.2")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .param("color", "#222222")
                        .param("icon", "fa-bicycle-new")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.updated"))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> updated = configs.stream().filter(c -> c.mode() == TransportMode.CYCLING).findFirst();
        assertThat(updated).isPresent();
        assertThat(updated.get().minKmh()).isEqualTo(10.0);
        assertThat(updated.get().maxKmh()).isEqualTo(30.0);
        assertThat(updated.get().preferenceWeight()).isEqualTo(1.2);
        assertThat(updated.get().color()).isEqualTo("#222222");
        assertThat(updated.get().icon()).isEqualTo("fa-bicycle-new");
    }

    @Test
    void updateTransportMode_Imperial_ShouldConvertSpeedsToKmh() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.DRIVING, 20.0, 100.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/update", TransportMode.DRIVING.name()).with(csrf())
                        .param("minSpeed", "10.0")
                        .param("maxSpeed", "75.0")
                        .param("unitSystem", UnitSystem.IMPERIAL.name())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.updated"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> driving = configs.stream().filter(c -> c.mode() == TransportMode.DRIVING).findFirst();
        assertThat(driving).isPresent();
        assertThat(driving.get().minKmh()).isCloseTo(16.0934, within(0.001));
        assertThat(driving.get().maxKmh()).isCloseTo(120.7005, within(0.001));
    }

    @Test
    void updateTransportMode_NullSpeeds_ShouldRetainExistingRange() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 6.5, "#f1ba63", "fa-person-walking")
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/update", TransportMode.WALKING.name()).with(csrf())
                        .param("color", "#a29bfe")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.updated"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> walking = configs.stream().filter(c -> c.mode() == TransportMode.WALKING).findFirst();
        assertThat(walking).isPresent();
        assertThat(walking.get().minKmh()).isEqualTo(0.0);
        assertThat(walking.get().maxKmh()).isEqualTo(6.5);
        assertThat(walking.get().color()).isEqualTo("#a29bfe");
        assertThat(walking.get().icon()).isEqualTo("fa-person-walking");
    }

    @Test
    void updateTransportMode_EmptyColorAndIcon_ShouldSetToNull() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0, "#f1ba63", "fa-person-walking")
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/update", TransportMode.WALKING.name()).with(csrf())
                        .param("minSpeed", "0.0")
                        .param("maxSpeed", "5.0")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .param("color", "")
                        .param("icon", "")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.updated"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        Optional<TransportModeConfig> walking = configs.stream().filter(c -> c.mode() == TransportMode.WALKING).findFirst();
        assertThat(walking).isPresent();
        assertThat(walking.get().color()).isNull();
        assertThat(walking.get().icon()).isNull();
    }

    @Test
    void updateTransportMode_InvalidRange_ShouldShowErrorMessage() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 20.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/update", TransportMode.CYCLING.name()).with(csrf())
                        .param("minSpeed", "25.0")
                        .param("maxSpeed", "10.0")
                        .param("unitSystem", UnitSystem.METRIC.name())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("errorMessage", "transportation.modes.error.invalid.range"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(configs.getFirst().minKmh()).isEqualTo(5.0);
        assertThat(configs.getFirst().maxKmh()).isEqualTo(20.0);
    }

    @Test
    void updateSensitivity_ShouldStoreValue() throws Exception {
        mockMvc.perform(post("/settings/transportation-modes/sensitivity").with(csrf())
                        .param("modeChangeSensitivity", "4.0")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.sensitivity"))
                .andExpect(model().attribute("modeChangeSensitivity", 4.0));

        assertThat(transportModeJdbcService.getModeChangeSensitivity(testUser)).isEqualTo(4.0);
    }

    @Test
    void deleteTransportMode_ShouldRemoveModeFromDatabase() throws Exception {
        transportModeJdbcService.setTransportModeConfigs(testUser, List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0),
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 20.0)
        ));

        mockMvc.perform(post("/settings/transportation-modes/{mode}/delete", TransportMode.CYCLING.name()).with(csrf())
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attribute("successMessage", "transportation.modes.success.deleted"))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).mode()).isEqualTo(TransportMode.WALKING);
    }

    @Test
    void getContent_ShouldReturnTransportationModesContentFragment() throws Exception {
        mockMvc.perform(post("/settings/transportation-modes/content").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: transportation-modes-content"))
                .andExpect(model().attributeExists("configs", "availableModes", "unitSystem", "isImperial", "modeChangeSensitivity"));
    }

    @Test
    void reclassifyTrips_ShouldEnqueueTaskAndReturnStartedStatus() throws Exception {
        mockMvc.perform(post("/settings/transportation-modes/reclassify").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/transportation-modes :: reclassify-status"))
                .andExpect(model().attribute("reclassifyStatus", "started"))
                .andExpect(model().attribute("message", "transportation.modes.reclassify.started"));
    }
}
