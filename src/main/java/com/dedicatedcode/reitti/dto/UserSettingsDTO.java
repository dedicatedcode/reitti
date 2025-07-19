package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.UnitSystem;

import java.util.List;

public record UserSettingsDTO(boolean preferColoredMap, String selectedLanguage,
                              List<ConnectedUserAccount> connectedUserAccounts, UnitSystem unitSystem, TilesCustomizationDTO tiles) {

    public record TilesCustomizationDTO(String service, String attribution){}

}
