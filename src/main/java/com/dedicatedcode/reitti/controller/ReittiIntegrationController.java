package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/reitti-integration")
public class ReittiIntegrationController {
    private final JdbcTemplate jdbcTemplate;
    private final ReittiIntegrationService reittiIntegrationService;

    public ReittiIntegrationController(JdbcTemplate jdbcTemplate, ReittiIntegrationService reittiIntegrationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.reittiIntegrationService = reittiIntegrationService;
    }

    @GetMapping("/avatar/{integrationId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long integrationId) {

        Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT mime_type, binary_data FROM remote_user_info WHERE integration_id = ?",
                integrationId
        );

        String contentType = (String) result.get("mime_type");
        byte[] imageData = (byte[]) result.get("binary_data");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(imageData.length);
        headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS));

        return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
    }


    @GetMapping(value = "/raw-location-points/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getRawLocationPoints(@AuthenticationPrincipal User user,
                                                  @PathVariable Long integrationId,
                                                  @RequestParam("date") String dateStr,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        return ResponseEntity.ok(Map.of("points", reittiIntegrationService.getRawLocationData(user, integrationId, dateStr, timezone)));
    }
}
