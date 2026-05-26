package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.MapLibreStyleDefinition;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import com.dedicatedcode.reitti.service.RequestHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class MapStyleControllerAdvice {

    private final MapLibreMapStylesService mapLibreMapStylesService;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final ObjectMapper objectMapper;

    public MapStyleControllerAdvice(MapLibreMapStylesService mapLibreMapStylesService, UserMapStyleJdbcService userMapStyleJdbcService, ObjectMapper objectMapper) {
        this.mapLibreMapStylesService = mapLibreMapStylesService;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.objectMapper = objectMapper;
    }


    @ModelAttribute("mapStylesJson")
    public List<MapLibreStyleDefinition> getMapStylesConfiguration(@AuthenticationPrincipal User user) {
        return this.mapLibreMapStylesService.getConfig(user);
    }

    @ModelAttribute("activeMapStyleId")
    public String getCurrentUserActiveMapStyleId(@AuthenticationPrincipal User user) {
        return this.userMapStyleJdbcService.getActiveStyleId(user);
    }
}
