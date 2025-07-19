package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

class TilesCustomizationProviderTest {

    @Test
    void getTilesConfiguration_WithCustomServiceAndAttribution_ShouldReturnCustomValues() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "https://custom.tiles.com/{z}/{x}/{y}.png";
        String customAttribution = "Custom Attribution";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(customService);
        assertThat(result.attribution()).isEqualTo(customAttribution);
    }

    @Test
    void getTilesConfiguration_WithEmptyCustomService_ShouldReturnDefaultService() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "";
        String customAttribution = "Custom Attribution";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(defaultService);
        assertThat(result.attribution()).isEqualTo(customAttribution);
    }

    @Test
    void getTilesConfiguration_WithEmptyCustomAttribution_ShouldReturnDefaultAttribution() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "https://custom.tiles.com/{z}/{x}/{y}.png";
        String customAttribution = "";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(customService);
        assertThat(result.attribution()).isEqualTo(defaultAttribution);
    }

    @Test
    void getTilesConfiguration_WithNullCustomValues_ShouldReturnDefaultValues() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = null;
        String customAttribution = null;
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(defaultService);
        assertThat(result.attribution()).isEqualTo(defaultAttribution);
    }

    @Test
    void getTilesConfiguration_WithWhitespaceOnlyCustomValues_ShouldReturnDefaultValues() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "   ";
        String customAttribution = "\t\n";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(defaultService);
        assertThat(result.attribution()).isEqualTo(defaultAttribution);
    }

    @Test
    void getTilesConfiguration_WithBothEmptyCustomValues_ShouldReturnDefaultValues() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "";
        String customAttribution = "";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(defaultService);
        assertThat(result.attribution()).isEqualTo(defaultAttribution);
    }

    @Test
    void getTilesConfiguration_WithValidCustomServiceOnly_ShouldReturnCustomServiceAndDefaultAttribution() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "https://custom.tiles.com/{z}/{x}/{y}.png";
        String customAttribution = "";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(customService);
        assertThat(result.attribution()).isEqualTo(defaultAttribution);
    }

    @Test
    void getTilesConfiguration_WithValidCustomAttributionOnly_ShouldReturnDefaultServiceAndCustomAttribution() {
        // Given
        String defaultService = "https://default.tiles.com/{z}/{x}/{y}.png";
        String defaultAttribution = "Default Attribution";
        String customService = "";
        String customAttribution = "Custom Attribution";
        
        TilesCustomizationProvider provider = new TilesCustomizationProvider(
            defaultService, defaultAttribution, customService, customAttribution
        );

        // When
        UserSettingsDTO.TilesCustomizationDTO result = provider.getTilesConfiguration();

        // Then
        assertThat(result.service()).isEqualTo(defaultService);
        assertThat(result.attribution()).isEqualTo(customAttribution);
    }
}
