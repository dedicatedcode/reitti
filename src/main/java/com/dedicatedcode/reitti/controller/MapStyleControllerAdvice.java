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

    /**
     * The JSON is inlined unescaped into a script block of several templates. Style names are user supplied (and
     * shared styles are shown to everyone), so characters that could terminate the script element are escaped.
     * The escapes are only valid inside JSON strings, which is the only place these characters can occur.
     */
    @ModelAttribute("mapStylesJson")
    public String getMapStylesConfiguration(@AuthenticationPrincipal User user) throws JacksonException {
        if (user == null) { return null; }
        return escapeForScript(this.objectMapper.writeValueAsString(this.mapLibreMapStylesService.getConfig(user)));
    }

    static String escapeForScript(String json) {
        return json
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    @ModelAttribute("activeMapStyleId")
    public Long getCurrentUserActiveMapStyleId(@AuthenticationPrincipal User user) {
        if (user == null) { return null; }
        return this.userMapStyleJdbcService.getActiveStyleId(user);
    }
}
