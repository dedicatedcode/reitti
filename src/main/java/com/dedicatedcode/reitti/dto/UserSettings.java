package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.UnitSystem;

import java.util.List;

public record UserSettings(boolean preferColoredMap, String selectedLanguage,
                           List<ConnectedUserAccount> connectedUserAccounts, UnitSystem unitSystem) {

}
