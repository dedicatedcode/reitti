
package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class DeviceSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private DeviceJdbcService deviceJdbcService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = testingService.admin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getPage_ShouldDisplayDeviceList() throws Exception {
        mockMvc.perform(get("/settings/devices").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/devices"))
                .andExpect(model().attributeExists("devices", "defaultColors", "activeSection", "isAdmin"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createDevice_ShouldPersistAndReturnFragment() throws Exception {
        // Given
        String deviceName = "TestDevice_" + System.currentTimeMillis();
        String color = "#4ecdc4";

        // When
        mockMvc.perform(post("/settings/devices")
                                .param("name", deviceName)
                                .param("color", color)
                                .param("enabled", "true")
                                .param("showOnMap", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/devices :: devices-content"))
                .andExpect(model().attributeExists("devices", "successMessage"));

        // Then
        List<Device> devices = deviceJdbcService.getAll(admin);
        Optional<Device> created = devices.stream()
                .filter(d -> d.name().equals(deviceName))
                .findFirst();
        assertThat(created).isPresent();
        assertThat(created.get().color()).isEqualTo(color);
        assertThat(created.get().enabled()).isTrue();
        assertThat(created.get().showOnMap()).isTrue();
        assertThat(created.get().defaultDevice()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateDevice_ShouldModifyExistingDevice() throws Exception {
        // Given
        Device device = createDevice("UpdateTest", "#ff6b6b");

        // When
        mockMvc.perform(post("/settings/devices/{deviceId}", device.id())
                                .param("name", "UpdatedName")
                                .param("color", "#45b7d1")
                                .param("enabled", "false")
                                .param("showOnMap", "false").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/devices :: devices-content"))
                .andExpect(model().attributeExists("devices", "successMessage"));

        // Then
        List<Device> devices = deviceJdbcService.getAll(admin);
        Device updated = devices.stream()
                .filter(d -> d.id().equals(device.id()))
                .findFirst()
                .orElseThrow();
        assertThat(updated.name()).isEqualTo("UpdatedName");
        assertThat(updated.color()).isEqualTo("#45b7d1");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.showOnMap()).isFalse();
        assertThat(updated.version()).isEqualTo(device.version() + 1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void toggleDevice_ShouldFlipEnabledFlag() throws Exception {
        // Given
        Device device = createDevice("ToggleTest", "#96ceb4");
        boolean originalEnabled = device.enabled();

        // When
        mockMvc.perform(post("/settings/devices/{deviceId}/toggle", device.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));

        // Then
        Device toggled = deviceJdbcService.getAll(admin).stream()
                .filter(d -> d.id().equals(device.id()))
                .findFirst()
                .orElseThrow();
        assertThat(toggled.enabled()).isEqualTo(!originalEnabled);
        assertThat(toggled.version()).isEqualTo(device.version() + 1);
    }


    @Test
    void setToDefault_ShouldMakeDeviceDefaultAndUnsetPreviousDefault() throws Exception {
        Device newDevice = createDevice("NewDefault", "#a29bfe");
        assertThat(newDevice.defaultDevice()).isFalse();

        // Find the current default device
        Device previousDefault = testingService.findDefaultDevice(admin);

        // When: set the new device as default
        mockMvc.perform(post("/settings/devices/{deviceId}/set-default", newDevice.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));

        // Then: exactly one device is default, and it is the one we just set
        List<Device> allDevices = deviceJdbcService.getAll(admin);
        List<Device> defaultDevices = allDevices.stream()
                .filter(Device::defaultDevice)
                .toList();
        assertThat(defaultDevices).hasSize(1);
        assertThat(defaultDevices.get(0).id()).isEqualTo(newDevice.id());

        // Verify the previous default is no longer default
        Device previousDefaultAfter = allDevices.stream()
                .filter(d -> d.id().equals(previousDefault.id()))
                .findFirst()
                .orElseThrow();
        assertThat(previousDefaultAfter.defaultDevice()).isFalse();
    }

    @Test
    void deleteDevice_ShouldRemoveNonDefaultDevice() throws Exception {
        // Given
        Device device = createDevice("DeleteTest", "#ffeaa7");
        assertThat(device.defaultDevice()).isFalse();

        // When
        mockMvc.perform(post("/settings/devices/{deviceId}/delete", device.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));

        // Then
        List<Device> remaining = deviceJdbcService.getAll(admin);
        assertThat(remaining).noneMatch(d -> d.id().equals(device.id()));
    }

    @Test
    void deleteDefaultDevice_ShouldShowError() throws Exception {
        // Given
        Device defaultDevice = deviceJdbcService.getAll(admin).stream()
                .filter(Device::defaultDevice)
                .findFirst()
                .orElseThrow();

        // When
        mockMvc.perform(post("/settings/devices/{deviceId}/delete", defaultDevice.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        // Then
        Device stillExists = deviceJdbcService.getAll(admin).stream()
                .filter(d -> d.id().equals(defaultDevice.id()))
                .findFirst()
                .orElseThrow();
        assertThat(stillExists.defaultDevice()).isTrue();
    }

    @Test
    void editDevice_ShouldReturnEditFormFragment() throws Exception {
        // Given
        Device device = deviceJdbcService.getAll(admin).get(0);

        // When
        mockMvc.perform(get("/settings/devices/edit/{deviceId}", device.id()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/devices :: device-edit-form"))
                .andExpect(model().attributeExists("device", "selectedColor", "defaultColors"));
    }

    private Device createDevice(String name, String color) {
        Device device = new Device(null, name, true, true, color, false,
                                   java.time.Instant.now(), java.time.Instant.now(), 1L);
        return deviceJdbcService.save(device, admin);
    }
}