package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ControllerAdvice
public class MapStyleControllerAdvice {

    private final MapLibreMapStylesService mapLibreMapStylesService;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final ObjectMapper objectMapper;

    public MapStyleControllerAdvice(MapLibreMapStylesService mapLibreMapStylesService,
                                    UserMapStyleJdbcService userMapStyleJdbcService,
                                    ObjectMapper objectMapper) {
        this.mapLibreMapStylesService = mapLibreMapStylesService;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.objectMapper = objectMapper;
    }

    @ModelAttribute("mapStylesJson")
    public String getMapStylesConfiguration(@AuthenticationPrincipal User user) throws JacksonException {
        if (user == null) { return null; }
        return this.objectMapper.writeValueAsString(this.mapLibreMapStylesService.getConfig(user));
    }

    @ModelAttribute("activeMapStyleId")
    public Long getCurrentUserActiveMapStyleId(@AuthenticationPrincipal User user) {
        if (user == null) { return null; }
        return this.userMapStyleJdbcService.getActiveStyleId(user);
    }
}
